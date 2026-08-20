package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.DictMapper;
import cn.aslight.workhub.model.system.DictQueryRequest;
import cn.aslight.workhub.model.system.DictResponse;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DictService {
    private final DictMapper mapper;
    public DictService(DictMapper mapper) { this.mapper = mapper; }
    public List<DictResponse> query(DictQueryRequest request) {
        return mapper.query(request == null ? new DictQueryRequest() : request);
    }
}
