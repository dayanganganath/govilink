package com.govilink.farmer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Test
    void getByIdReturnsExistingFarmer() {
        UUID id = UUID.randomUUID();
        Farmer farmer = mock(Farmer.class);

        when(farmer.getId()).thenReturn(id);
        when(farmer.getFullName()).thenReturn("Demo Farmer");
        when(repository.findById(id))
                .thenReturn(Optional.of(farmer));

        var response = service.getById(id);

        assertAll(
                () -> assertEquals(id, response.id()),
                () -> assertEquals("Demo Farmer", response.fullName())
        );

        verify(repository).findById(id);
    }

    @Test
    void getByIdReturnsNotFoundWhenFarmerDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(repository.findById(id))
                .thenReturn(Optional.empty());

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getById(id)
        );

        assertEquals(404, exception.getStatusCode().value());

        verify(repository).findById(id);
    }

    @Test
    void updateChangesAndNormalizesDetailsWithMatchingVersion() {
        UUID id = UUID.randomUUID();

        Farmer farmer = new Farmer(
                "Original Farmer",
                "TEST-001",
                "Matale",
                "Original Village"
        );

        when(repository.findById(id))
                .thenReturn(Optional.of(farmer));

        when(repository.saveAndFlush(farmer))
                .thenReturn(farmer);

        var request = new FarmerUpdateRequest(
                "  Updated Farmer  ",
                "  TEST-002  ",
                "  Kandy  ",
                "   ",
                0L
        );

        var response = service.update(id, request);

        assertAll(
                () -> assertEquals("Updated Farmer", response.fullName()),
                () -> assertEquals("TEST-002", response.phone()),
                () -> assertEquals("Kandy", response.district()),
                () -> assertNull(response.village()),
                () -> assertTrue(response.active())
        );

        verify(repository).findById(id);
        verify(repository).saveAndFlush(farmer);
    }

    @Test
    void updateReturnsNotFoundWithoutSavingWhenFarmerDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(repository.findById(id))
                .thenReturn(Optional.empty());

        var request = new FarmerUpdateRequest(
                "Updated Farmer",
                "TEST-002",
                "Kandy",
                null,
                0L
        );

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.update(id, request)
        );

        assertEquals(404, exception.getStatusCode().value());

        verify(repository).findById(id);
        verify(repository, never()).saveAndFlush(any(Farmer.class));
    }

    @Test
    void updateRejectsStaleVersionWithoutChangingOrSavingFarmer() {
        UUID id = UUID.randomUUID();
        Farmer farmer = mock(Farmer.class);

        when(repository.findById(id))
                .thenReturn(Optional.of(farmer));

        when(farmer.getVersion()).thenReturn(2L);

        var request = new FarmerUpdateRequest(
                "Updated Farmer",
                "TEST-002",
                "Kandy",
                "Updated Village",
                1L
        );

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.update(id, request)
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(farmer, never()).updateDetails(
                anyString(),
                anyString(),
                anyString(),
                any()
        );

        verify(repository, never()).saveAndFlush(any(Farmer.class));
    }

    @Test
    void updatePropagatesDatabaseOptimisticLockConflict() {
        UUID id = UUID.randomUUID();

        Farmer farmer = new Farmer(
                "Original Farmer",
                "TEST-001",
                "Matale",
                null
        );

        when(repository.findById(id))
                .thenReturn(Optional.of(farmer));

        var conflict = new ObjectOptimisticLockingFailureException(
                Farmer.class,
                id
        );

        when(repository.saveAndFlush(farmer))
                .thenThrow(conflict);

        var request = new FarmerUpdateRequest(
                "Updated Farmer",
                "TEST-002",
                "Kandy",
                null,
                0L
        );

        var exception = assertThrows(
                ObjectOptimisticLockingFailureException.class,
                () -> service.update(id, request)
        );

        assertSame(conflict, exception);

        verify(repository).saveAndFlush(farmer);
    }
}