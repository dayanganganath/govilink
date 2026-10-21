package com.govilink.farmer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FarmerSearchServiceTest {

    @Mock
    private FarmerRepository repository;

    @InjectMocks
    private FarmerSearchService service;

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, 101"
    })
    void invalidPaginationIsRejectedBeforeDatabaseAccess(
            int page,
            int size
    ) {
        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.search(
                        null,
                        null,
                        null,
                        page,
                        size
                )
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test
    void oversizedQueryIsRejectedBeforeDatabaseAccess() {
        String query = "a".repeat(151);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.search(
                        query,
                        null,
                        null,
                        0,
                        20
                )
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test
    void oversizedDistrictIsRejectedBeforeDatabaseAccess() {
        String district = "a".repeat(101);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.search(
                        null,
                        district,
                        null,
                        0,
                        20
                )
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test
    void searchReturnsFarmerContentAndPaginationMetadata() {
        PageRequest pageable = expectedPage(1, 2);

        Farmer first = new Farmer(
                "First Farmer",
                "TEST-001",
                "Kandy",
                "Village A"
        );

        Farmer second = new Farmer(
                "Second Farmer",
                "TEST-002",
                "Kandy",
                "Village B"
        );

        Page<Farmer> databasePage = new PageImpl<>(
                List.of(first, second),
                pageable,
                7
        );

        when(repository.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Farmer>>any(),
                eq(pageable)
        )).thenReturn(databasePage);

        var response = service.search(
                "farmer",
                "Kandy",
                true,
                1,
                2
        );

        assertAll(
                () -> assertEquals(2, response.content().size()),
                () -> assertEquals(
                        "First Farmer",
                        response.content().get(0).fullName()
                ),
                () -> assertEquals(
                        "Second Farmer",
                        response.content().get(1).fullName()
                ),
                () -> assertEquals(1, response.page()),
                () -> assertEquals(2, response.size()),
                () -> assertEquals(7L, response.totalElements()),
                () -> assertEquals(4, response.totalPages()),
                () -> assertTrue(response.hasNext()),
                () -> assertTrue(response.hasPrevious())
        );

        verify(repository).findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Farmer>>any(),
                eq(pageable)
        );
    }

    @Test
    void searchReturnsEmptyContentWhenNoFarmersMatch() {
        PageRequest pageable = expectedPage(0, 20);

        when(repository.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Farmer>>any(),
                eq(pageable)
        )).thenReturn(Page.<Farmer>empty(pageable));

        var response = service.search(
                "No matching farmer",
                null,
                null,
                0,
                20
        );

        assertAll(
                () -> assertTrue(response.content().isEmpty()),
                () -> assertEquals(0, response.page()),
                () -> assertEquals(20, response.size()),
                () -> assertEquals(0L, response.totalElements()),
                () -> assertEquals(0, response.totalPages()),
                () -> assertFalse(response.hasNext()),
                () -> assertFalse(response.hasPrevious())
        );
    }

    private PageRequest expectedPage(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
    }
}