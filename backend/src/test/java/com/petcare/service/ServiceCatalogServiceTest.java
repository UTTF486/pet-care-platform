package com.petcare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.common.BusinessException;
import com.petcare.common.ResultCode;
import com.petcare.entity.ServiceItem;
import com.petcare.mapper.CategoryMapper;
import com.petcare.mapper.ServiceItemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ServiceCatalogServiceTest {

    private final ServiceItemMapper serviceItemMapper = mock(ServiceItemMapper.class);
    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ServiceCatalogService service = new ServiceCatalogService(
            serviceItemMapper, categoryMapper, redisTemplate, new ObjectMapper());

    @Test
    void changeStatusRejectsMissingService() {
        when(serviceItemMapper.updateStatus(99L, 1)).thenReturn(0);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.changeStatus(99L, 1));

        assertEquals(ResultCode.NOT_FOUND.getCode(), error.getCode());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void saveRejectsMissingCategory() {
        ServiceItem item = new ServiceItem();
        item.setCategoryId(999L);
        item.setName("测试服务");
        item.setPrice(new java.math.BigDecimal("10.00"));
        when(categoryMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.save(item));
    }

    @Test
    void updateRejectsChangingToMissingCategory() {
        ServiceItem item = new ServiceItem();
        item.setId(3L);
        item.setCategoryId(999L);
        when(categoryMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.update(item));

        verifyNoInteractions(redisTemplate);
    }
}
