package com.govilink.harvest;

import com.govilink.farmer.Farmer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class HarvestCollectionSchemaIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    private UUID harvestId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        Farmer farmer = new Farmer(
                "Collection Schema Farmer",
                "CS-" + unique,
                "Matale",
                "Test Village"
        );

        entityManager.persist(farmer);
        entityManager.flush();

        HarvestListing harvest = new HarvestListing(
                farmer,
                "Tomato",
                amount("100"),
                amount("200"),
                LocalDate.of(2030, 1, 1)
        );

        entityManager.persist(harvest);
        entityManager.flush();

        harvestId = harvest.getId();
    }

    @Test
    void persistsCollectionBalancesAndVersion() {
        HarvestListing harvest = entityManager.find(
                HarvestListing.class,
                harvestId
        );

        long originalVersion = harvest.getVersion();

        harvest.reserve(amount("60"));
        harvest.collectReservation(amount("25"));

        entityManager.flush();
        entityManager.clear();

        HarvestListing saved = entityManager.find(
                HarvestListing.class,
                harvestId
        );

        assertAmount("100", saved.getQuantityKg());
        assertAmount("35", saved.getReservedQuantityKg());
        assertAmount("25", saved.getCollectedQuantityKg());
        assertAmount("40", saved.getAvailableQuantityKg());

        assertTrue(saved.getVersion() > originalVersion);
    }

    @Test
    void databaseRejectsCollectedStockAboveTheAvailableBalance() {
        entityManager.createNativeQuery(
                """
                update harvest_listings
                set reserved_quantity_kg = 60
                where id = :id
                """
        )
                .setParameter("id", harvestId)
                .executeUpdate();

        assertThrows(
                PersistenceException.class,
                () -> entityManager.createNativeQuery(
                        """
                        update harvest_listings
                        set collected_quantity_kg = 40.001
                        where id = :id
                        """
                )
                        .setParameter("id", harvestId)
                        .executeUpdate()
        );
    }

    @Test
    void databaseRejectsNegativeCollectedStock() {
        assertThrows(
                PersistenceException.class,
                () -> entityManager.createNativeQuery(
                        """
                        update harvest_listings
                        set collected_quantity_kg = -0.001
                        where id = :id
                        """
                )
                        .setParameter("id", harvestId)
                        .executeUpdate()
        );
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(
                0,
                amount(expected).compareTo(actual)
        );
    }
}