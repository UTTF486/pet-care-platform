package com.petcare.service;

import com.petcare.common.BusinessException;
import com.petcare.entity.Servicer;
import com.petcare.mapper.ServicerMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

class ServicerServiceTest {

    @Test
    void emptyUpdateIsRejectedBeforeSqlExecution() {
        ServicerMapper mapper = Mockito.mock(ServicerMapper.class);
        ServicerService service = new ServicerService(mapper);
        Servicer request = new Servicer();
        request.setId(1L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.update(request));

        assertEquals(400, error.getCode());
        verifyNoInteractions(mapper);
    }
}
