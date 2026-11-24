# 设计

REQ-9b0b2dce-67a2-4cf8-b9cd-c4ba248fe3b1；workhub-web 配置调整，workhub-server 仅归档。

当前前端绑定 127.0.0.1，开发环境 API 为 http://127.0.0.1:8080/api。同事浏览器无法访问前端，且接口会指向同事本机。

Vite server 设置 host=0.0.0.0、port=9091、strictPort=true；/api 代理到 http://127.0.0.1:8080，保留路径，changeOrigin=true。开发环境 API 设为 /api。沿用 Vite 默认允许 IP 主机访问的校验，不设置 allowedHosts=true，不调整后端 CORS。

验证已有依赖构建、监听地址、局域网 IP 下页面与未登录接口响应及浏览器登录页。回滚本次 vite.config.ts 与 .env.development 的修改并重启前端即可；不回滚其他工作区改动。
