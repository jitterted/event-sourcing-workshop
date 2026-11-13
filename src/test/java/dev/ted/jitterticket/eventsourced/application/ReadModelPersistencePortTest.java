package dev.ted.jitterticket.eventsourced.application;

import dev.ted.jitterticket.eventsourced.domain.customer.CustomerId;
import org.junit.jupiter.api.Test;

import static dev.ted.jitterticket.eventsourced.application.Assertions.assertThat;

class ReadModelPersistencePortTest {

    @Test
    void emptyPersistenceLoadsProjectorWithEmptyStateAndCheckpointAtInitial() {
        var projectionPersistence = new NewMemoryRegisteredCustomersReadModelPersistence();

        var projector = projectionPersistence.loadReadModel();

        assertThat(projector.current())
                .hasCheckpointValueOf(0)
                .hasEmptyState();
    }

    @Test
    void savedDeltaWithEmptyDeltaBumpsCheckpoint() {
        // this is the situation where a projector saw new events, but its state didn't change (non-relevant events)
        var projectionPersistence =
                new NewMemoryRegisteredCustomersReadModelPersistence();

        projectionPersistence.saveDelta(
                new Checkpointed<>(new NewlyRegisteredCustomers(), Checkpoint.of(1)));

        var projector = projectionPersistence.loadReadModel();

        assertThat(projector.current())
                .hasCheckpointValueOf(1)
                .hasEmptyState();
    }

    @Test
    void deltaWithNewDataSavedToEmptyPersistenceIsReturnedInProjector() {
        var projectionPersistence =
                new NewMemoryRegisteredCustomersReadModelPersistence();

        RegisteredCustomer registeredCustomer = new RegisteredCustomer(CustomerId.createRandom(), "Customer Name");
        NewlyRegisteredCustomers delta = NewlyRegisteredCustomers
                .createForTestWith(registeredCustomer);
        projectionPersistence.saveDelta(new Checkpointed<>(delta, Checkpoint.of(1)));

        var projector = projectionPersistence.loadReadModel();

        assertThat(projector.current())
                .hasCheckpointValueOf(1)
                .state()
                .isEqualTo(AllRegisteredCustomers.of(registeredCustomer));
    }

    @Test
    void savedDeltaIsMergedWithExistingState() {
        var projectionPersistence = new NewMemoryRegisteredCustomersReadModelPersistence();
        Checkpoint checkpoint = Checkpoint.of(1);
        projectionPersistence.saveDelta(
                new Checkpointed<>(createDeltaWith("Existing Customer"
                ), checkpoint));

        Checkpoint deltaCheckpoint = Checkpoint.of(2);
        NewlyRegisteredCustomers delta = createDeltaWith("New Customer"
        );
        projectionPersistence.saveDelta(new Checkpointed<>(delta, deltaCheckpoint));

        var projector = projectionPersistence.loadReadModel();

        assertThat(projector.current())
                .hasCheckpointValueOf(2);

        assertThat(projector.current().state().asList())
                .extracting(RegisteredCustomer::name)
                .contains("Existing Customer", "New Customer");
    }

    static NewlyRegisteredCustomers createDeltaWith(String customerName) {
        return NewlyRegisteredCustomers.createForTestWith(
                new RegisteredCustomer(
                        CustomerId.createRandom(),
                        customerName
                ));
    }

}