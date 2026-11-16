"""Fixed read-only helper run through SSH; no upload, installation or caller code."""
import codecs
import datetime
import errno
import json
import os
import stat
import subprocess
import sys
import zipfile


class Rejected(Exception):
    pass


def restricted(path):
    parts = path.lower().split('/')
    name = parts[-1]
    return (any(p in ('.ssh', '.aws', '.gnupg', '.kube', '.git') for p in parts)
            or name in ('credentials', 'shadow', 'gshadow', 'id_rsa', 'id_dsa', 'id_ecdsa', 'id_ed25519')
            or name == '.env' or name.startswith('.env.')
            or name.endswith(('.pem', '.key', '.p12', '.pfx', '.keystore', '.jks')))


def open_path(path, directory):
    # Directory descriptors anchor every component; no symlink check/open race.
    fd = os.open('/', os.O_RDONLY | os.O_DIRECTORY)
    try:
        parts = path.strip('/').split('/') if path != '/' else []
        for i, part in enumerate(parts):
            flags = os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK
            if directory or i < len(parts) - 1:
                flags |= os.O_DIRECTORY
            child = os.open(part, flags, dir_fd=fd)
            os.close(fd)
            fd = child
        result = fd
        fd = -1
        return result
    finally:
        if fd >= 0:
            os.close(fd)


def list_directory(fd, path, options):
    entries = []
    truncated = False

    def walk(directory_fd, directory_path, depth):
        nonlocal truncated
        with os.scandir(directory_fd) as children:
            for entry in children:
                if len(entries) >= options['maxEntries']:
                    truncated = True
                    return
                child_path = directory_path.rstrip('/') + '/' + entry.name
                info = entry.stat(follow_symlinks=False)
                kind = ('symlink' if stat.S_ISLNK(info.st_mode) else
                        'directory' if stat.S_ISDIR(info.st_mode) else
                        'file' if stat.S_ISREG(info.st_mode) else 'special')
                denied = restricted(child_path)
                entries.append({'path': child_path, 'type': kind, 'restricted': denied,
                                'sizeBytes': info.st_size,
                                'modifiedAt': datetime.datetime.fromtimestamp(
                                    info.st_mtime, datetime.timezone.utc).isoformat()})
                if kind == 'directory' and not denied:
                    if depth >= options['maxDepth'] or len(entries) >= options['maxEntries']:
                        truncated = True
                        continue
                    child_fd = os.open(entry.name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                                       dir_fd=directory_fd)
                    try:
                        walk(child_fd, child_path, depth + 1)
                    finally:
                        os.close(child_fd)
                    if truncated and len(entries) >= options['maxEntries']:
                        return

    walk(fd, path, 1)
    return {'path': path, 'entries': entries, 'truncated': truncated}


def read_file(fd, path, options):
    info = os.fstat(fd)
    if not stat.S_ISREG(info.st_mode):
        raise Rejected('仅允许读取普通文本文件')
    raw = bytearray()
    while len(raw) <= options['maxBytes']:
        chunk = os.read(fd, min(65536, options['maxBytes'] + 1 - len(raw)))
        if not chunk:
            break
        raw.extend(chunk)
    truncated = len(raw) > options['maxBytes']
    raw = bytes(raw[:options['maxBytes']])
    if any(b < 32 and b not in (9, 10, 13) for b in raw):
        raise Rejected('不支持二进制文件或含控制字符的文本')
    try:
        content = codecs.getincrementaldecoder('utf-8')().decode(raw, final=not truncated)
    except UnicodeDecodeError:
        raise Rejected('仅支持 UTF-8 文本文件')
    lines = content.splitlines(keepends=True)
    truncated = truncated or len(lines) > options['lines']
    return {'path': path, 'sizeBytes': info.st_size,
            'content': ''.join(lines[:options['lines']]), 'truncated': truncated}


def read_log(fd, path, options):
    if not stat.S_ISREG(os.fstat(fd).st_mode):
        raise Rejected('仅允许读取普通日志文件')
    # 只传递已用 O_NOFOLLOW 打开的描述符，日志命令不会重新解析原路径。
    descriptor_path = '/dev/fd/' + str(fd)
    if options['operation'] == 'tail_log_file':
        command = ['tail', '-n', str(options['lines']), descriptor_path]
    elif path.lower().endswith('.zip'):
        search_zip(fd, options)
    else:
        command = ['grep', '-F', '-n', '-m', str(options['maxMatches']), '--', options['keyword'], descriptor_path]
    sys.exit(subprocess.run(command, pass_fds=(fd,)).returncode)


def search_zip(fd, options):
    # zipgrep 的 shell 包装可能关闭传入的描述符；直接流式检索 ZIP，不重新打开原路径。
    matched = 0
    keyword = options['keyword'].encode('utf-8')
    try:
        with os.fdopen(os.dup(fd), 'rb') as stream, zipfile.ZipFile(stream) as archive:
            if len(archive.infolist()) > 1000:
                raise Rejected('ZIP 日志条目过多，请缩小读取范围')
            for entry in archive.infolist():
                if entry.is_dir():
                    continue
                if restricted(entry.filename):
                    raise Rejected('ZIP 日志包含敏感凭据条目，拒绝读取')
                with archive.open(entry) as content:
                    line_number = 0
                    while True:
                        line = content.readline(262145)
                        if not line:
                            break
                        if len(line) > 262144:
                            raise Rejected('ZIP 日志单行超过字节上限')
                        line_number += 1
                        if keyword not in line:
                            continue
                        label = entry.filename.replace('\n', '\\n').replace('\r', '\\r')
                        print('{}:{}:{}'.format(label, line_number, line.decode('utf-8', errors='replace').rstrip('\r\n')))
                        matched += 1
                        if matched >= options['maxMatches']:
                            sys.exit(0)
    except (zipfile.BadZipFile, NotImplementedError, RuntimeError):
        raise Rejected('不支持损坏、加密或当前格式的 ZIP 日志')
    sys.exit(0 if matched else 1)


def main():
    options = json.loads(sys.argv[1])
    path = options['path']
    if (not path.startswith('/') or '..' in path.split('/')
            or not any(root != '/' and (path == root or path.startswith(root + '/'))
                       for root in options['roots'])):
        raise Rejected('路径不在允许 AI 读取的白名单目录内，其他目录需用户明确授权')
    if restricted(path):
        raise Rejected('禁止读取敏感凭据文件或目录')
    directory = options['operation'] == 'list_server_directory'
    if not directory and options['operation'] not in ('read_server_file', 'tail_log_file', 'search_log_file'):
        raise Rejected('不支持的文件读取操作')
    fd = open_path(path, directory)
    try:
        if options['operation'] in ('tail_log_file', 'search_log_file'):
            read_log(fd, path, options)
        result = list_directory(fd, path, options) if directory else read_file(fd, path, options)
        print(json.dumps(result, ensure_ascii=True))
    finally:
        os.close(fd)


try:
    main()
except Rejected as error:
    print(json.dumps({'error': str(error)}, ensure_ascii=True))
    sys.exit(1)
except OSError as error:
    message = {errno.EACCES: '文件或目录读取权限不足', errno.ENOENT: '文件或目录不存在',
               errno.ELOOP: '禁止读取软链接', errno.ENOTDIR: '路径组件不是目录或属于软链接'}.get(
                   error.errno, '文件或目录读取失败')
    print(json.dumps({'error': message}, ensure_ascii=True))
    sys.exit(1)
