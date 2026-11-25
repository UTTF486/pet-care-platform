package com.petcare.service;

import com.petcare.common.BusinessException;
import com.petcare.common.OrderStatus;
import com.petcare.dto.OrderCreateDTO;
import com.petcare.entity.Order;
import com.petcare.entity.Pet;
import com.petcare.entity.ServiceItem;
import com.petcare.entity.Servicer;
import com.petcare.mapper.OrderLogMapper;
import com.petcare.mapper.OrderMapper;
import com.petcare.mapper.PetMapper;
import com.petcare.mapper.ReviewMapper;
import com.petcare.mapper.ServiceItemMapper;
import com.petcare.mapper.ServicerMapper;
import com.petcare.util.CommonUtil;
import com.petcare.util.OrderNoGenerator;
import com.petcare.vo.OrderVO;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final OrderLogMapper orderLogMapper = mock(OrderLogMapper.class);
    private final PetMapper petMapper = mock(PetMapper.class);
    private final ServiceItemMapper serviceItemMapper = mock(ServiceItemMapper.class);
    private final ServicerMapper servicerMapper = mock(ServicerMapper.class);
    private final ReviewMapper reviewMapper = mock(ReviewMapper.class);
    private final MessageService messageService = mock(MessageService.class);
    private final CommonUtil commonUtil = mock(CommonUtil.class);
    private final OrderNoGenerator orderNoGenerator = mock(OrderNoGenerator.class);
    private final OrderTimeoutService orderTimeoutService = mock(OrderTimeoutService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final OrderService orderService = new OrderService(
            orderMapper, orderLogMapper, petMapper, serviceItemMapper, servicerMapper,
            reviewMapper, messageService, commonUtil, orderNoGenerator, orderTimeoutService, redisTemplate);

    @Test
    void acceptRejectsExpiredAppointment() {
        Order order = pendingOrder(
                LocalDateTime.of(2026, 11, 25, 10, 0),
                LocalDateTime.of(2026, 11, 25, 13, 0));
        when(orderMapper.selectState(1L)).thenReturn(order);
        when(servicerMapper.lockAvailableById(7L)).thenReturn(new Servicer());
        when(commonUtil.now()).thenReturn(LocalDateTime.of(2026, 11, 25, 10, 0));

        assertThrows(BusinessException.class, () -> orderService.accept(1L, 7L));

        verify(orderMapper, never()).accept(any(), any());
    }

    @Test
    void acceptChecksFullAppointmentInterval() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 26, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 26, 13, 0);
        Order order = pendingOrder(start, end);
        when(orderMapper.selectState(1L)).thenReturn(order);
        when(servicerMapper.lockAvailableById(7L)).thenReturn(new Servicer());
        when(commonUtil.now()).thenReturn(LocalDateTime.of(2026, 11, 25, 18, 0));
        when(orderMapper.selectServicerScheduleConflicts(7L, start, end, 1L))
                .thenReturn(Collections.emptyList());
        when(orderMapper.accept(1L, 7L)).thenReturn(1);
        when(commonUtil.currentUserId()).thenReturn(99L);

        orderService.accept(1L, 7L);

        verify(orderMapper).selectServicerScheduleConflicts(7L, start, end, 1L);
        verify(servicerMapper).increaseOrderCount(7L);
    }

    @Test
    void acceptRejectsOverlappingInterval() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 26, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 26, 13, 0);
        Order order = pendingOrder(start, end);
        when(orderMapper.selectState(1L)).thenReturn(order);
        when(servicerMapper.lockAvailableById(7L)).thenReturn(new Servicer());
        when(commonUtil.now()).thenReturn(LocalDateTime.of(2026, 11, 25, 18, 0));
        when(orderMapper.selectServicerScheduleConflicts(7L, start, end, 1L))
                .thenReturn(Arrays.asList(8L));

        assertThrows(BusinessException.class, () -> orderService.accept(1L, 7L));

        verify(orderMapper, never()).accept(any(), any());
    }

    @Test
    void createReturnsExistingOrderForRepeatedRequestId() {
        OrderCreateDTO dto = checkoutDto();
        dto.setRequestId("request-123");
        Order existingOrder = new Order();
        existingOrder.setId(9L);
        // 独立计算的期望值，避免测试用被测工具自己生成期望结果。
        existingOrder.setRequestHash(
                "a85470ec70e50f4086b844c233bde264534d086f36dff856a7e1aa6b39891569");
        when(commonUtil.currentUserId()).thenReturn(2L);
        when(orderMapper.selectByUserAndRequestId(2L, "request-123"))
                .thenReturn(existingOrder);
        OrderVO existing = new OrderVO();
        existing.setId(9L);
        when(orderMapper.selectDetail(9L)).thenReturn(existing);

        OrderVO result = orderService.create(dto);

        assertEquals(9L, result.getId());
        verify(petMapper, never()).selectByIdForUpdate(any(), any());
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void repeatedRequestIdWithDifferentPayloadIsRejected() {
        OrderCreateDTO dto = checkoutDto();
        dto.setRequestId("request-123");
        Order existingOrder = new Order();
        existingOrder.setId(9L);
        existingOrder.setRequestHash("different-payload-hash");
        when(commonUtil.currentUserId()).thenReturn(2L);
        when(orderMapper.selectByUserAndRequestId(2L, "request-123"))
                .thenReturn(existingOrder);

        assertThrows(BusinessException.class, () -> orderService.create(dto));

        verify(orderMapper, never()).insert(any());
    }

    @Test
    void insertConflictWithDifferentFingerprintIsRejected() {
        OrderCreateDTO dto = checkoutDto();
        dto.setRequestId("request-123");
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(2L);
        ServiceItem service = new ServiceItem();
        service.setId(3L);
        service.setName("测试服务");
        service.setStatus(1);
        service.setDuration(60);
        service.setPrice(new BigDecimal("10.00"));
        Order conflict = new Order();
        conflict.setId(9L);
        conflict.setRequestHash("different-payload-hash");
        when(commonUtil.currentUserId()).thenReturn(2L);
        when(petMapper.selectByIdForUpdate(1L, 2L)).thenReturn(pet);
        when(serviceItemMapper.selectById(3L)).thenReturn(service);
        when(commonUtil.now()).thenReturn(LocalDateTime.of(2026, 11, 25, 18, 0));
        when(commonUtil.buildExpireTime()).thenReturn(LocalDateTime.of(2026, 11, 25, 18, 30));
        when(orderNoGenerator.next()).thenReturn("PC2026112518000000000000000000c");
        when(orderMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(orderMapper.selectByUserAndRequestIdForUpdate(2L, "request-123"))
                .thenReturn(conflict);

        assertThrows(BusinessException.class, () -> orderService.create(dto));
    }

    @Test
    void urgeFailureCleansRedisDeduplicationKey() {
        Order order = pendingOrder(
                LocalDateTime.of(2026, 11, 26, 10, 0),
                LocalDateTime.of(2026, 11, 26, 11, 0));
        order.setUserId(2L);
        when(commonUtil.currentUserId()).thenReturn(2L);
        when(orderMapper.selectState(1L)).thenReturn(order);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("urge:order:1", "1", 5L, java.util.concurrent.TimeUnit.MINUTES))
                .thenReturn(true);
        doThrow(new RuntimeException("message insert failed"))
                .when(messageService).send(any(), any(), any(), any(), any());

        assertThrows(RuntimeException.class, () -> orderService.urge(1L));

        verify(redisTemplate).delete("urge:order:1");
    }

    private OrderCreateDTO checkoutDto() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setPetId(1L);
        dto.setServiceId(3L);
        dto.setAppointDate(java.time.LocalDate.of(2026, 11, 30));
        dto.setTimeSlot("10:00-12:00");
        dto.setContactName("小李");
        dto.setContactPhone("13800000002");
        dto.setAddress("测试地址");
        dto.setRemark("测试备注");
        return dto;
    }

    private Order pendingOrder(LocalDateTime start, LocalDateTime end) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("PC2026112500000000000000000000c");
        order.setUserId(2L);
        order.setServiceId(3L);
        order.setAppointmentStart(start);
        order.setAppointmentEnd(end);
        order.setStatus(OrderStatus.PENDING_ACCEPT);
        return order;
    }
}
