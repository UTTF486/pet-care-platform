package com.petcare.service;

import com.petcare.common.BusinessException;
import com.petcare.common.PageResult;
import com.petcare.common.ResultCode;
import com.petcare.entity.Servicer;
import com.petcare.mapper.ServicerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 服务师服务。接单时从"可接单"列表里选人。
 */
@Service
@RequiredArgsConstructor
public class ServicerService {

    private final ServicerMapper servicerMapper;

    /** 接单弹窗用：只列出可接单的服务师 */
    public List<Servicer> listAvailable() {
        return servicerMapper.selectAvailable();
    }

    public PageResult<Servicer> page(String keyword, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        long total = servicerMapper.countPage(keyword);
        if (total == 0) {
            return PageResult.empty(pageNum, pageSize);
        }
        return PageResult.of(total, pageNum, pageSize, servicerMapper.selectPage(keyword, offset, pageSize));
    }

    public Long save(Servicer servicer) {
        if (servicer.getStatus() == null) {
            servicer.setStatus(1);
        }
        if (servicer.getLevel() == null) {
            servicer.setLevel(1);
        }
        servicer.setPhone(servicer.getPhone() == null ? "" : servicer.getPhone());
        servicer.setSkills(servicer.getSkills() == null ? "" : servicer.getSkills());
        servicer.setAvatar(servicer.getAvatar() == null ? "" : servicer.getAvatar());
        servicerMapper.insert(servicer);
        return servicer.getId();
    }

    public void update(Servicer servicer) {
        if (servicer.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少服务师 id");
        }
        int rows = servicerMapper.update(servicer);
        if (rows == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "服务师不存在");
        }
    }
}
