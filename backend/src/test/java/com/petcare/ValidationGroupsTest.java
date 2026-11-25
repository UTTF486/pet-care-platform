package com.petcare;

import com.petcare.entity.Pet;
import com.petcare.entity.ServiceItem;
import org.junit.jupiter.api.Test;

import javax.validation.Validator;
import java.math.BigDecimal;

import static javax.validation.Validation.buildDefaultValidatorFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationGroupsTest {

    private final Validator validator = buildDefaultValidatorFactory().getValidator();

    @Test
    void petUpdateCanContainOnlyOneField() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setAge(24);

        assertTrue(validator.validate(pet, Pet.Update.class).isEmpty());
    }

    @Test
    void emptyPetNameIsStillRejectedOnUpdate() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setName("");

        assertEquals(1, validator.validate(pet, Pet.Update.class).size());
    }

    @Test
    void serviceCreateStillRequiresPriceAndName() {
        ServiceItem item = new ServiceItem();
        item.setCategoryId(1L);
        item.setPrice(new BigDecimal("49.00"));

        assertEquals(1, validator.validate(item, ServiceItem.Create.class).size());
    }
}
