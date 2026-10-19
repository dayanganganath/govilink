package com.govilink.farmer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.govilink.farmer.Farmer;
import com.govilink.farmer.FarmerDtos;
import com.govilink.farmer.FarmerRepository;
import com.govilink.farmer.FarmerService;
import com.govilink.farmer.FarmerDtos.CreateRequest;
import com.govilink.farmer.FarmerDtos.Response;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FarmerServiceTest {

    @Mock
    private FarmerRepository repository;

    @InjectMocks
    private FarmerService service;

    @Test
    void createTrimsInputBeforeSaving() {
        when(repository.saveAndFlush(any(Farmer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new FarmerDtos.CreateRequest(
                "  Demo Farmer  ",
                "  TEST-001  ",
                "  Matale  ",
                "  Demo Village  "
        );

        var response = service.create(request);

        assertAll(
                () -> assertEquals("Demo Farmer", response.fullName()),
                () -> assertEquals("TEST-001", response.phone()),
                () -> assertEquals("Matale", response.district()),
                () -> assertEquals("Demo Village", response.village()),
                () -> assertTrue(response.active())
        );

        verify(repository).saveAndFlush(any(Farmer.class));
    }

    @Test
    void createConvertsBlankVillageToNull() {
        when(repository.saveAndFlush(any(Farmer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new FarmerDtos.CreateRequest(
                "Demo Farmer",
                "TEST-001",
                "Matale",
                "   "
        );

        var response = service.create(request);

        assertNull(response.village());
    }

    @Test
    void listLatestRequestsAtMostFiftyFarmersInNewestFirstOrder() {
        var expectedPage = PageRequest.of(
                0,
                50,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        var farmer = new Farmer(
                "Demo Farmer",
                "TEST-001",
                "Matale",
                null
        );

        when(repository.findAll(expectedPage))
                .thenReturn(new PageImpl<>(List.of(farmer)));

        var results = service.listLatest();

        assertEquals(1, results.size());
        assertEquals("Demo Farmer", results.get(0).fullName());
        verify(repository).findAll(expectedPage);
    }
}