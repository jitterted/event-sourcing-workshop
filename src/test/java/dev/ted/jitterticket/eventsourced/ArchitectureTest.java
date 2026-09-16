package dev.ted.jitterticket.eventsourced;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import dev.ted.jitterticket.eventsourced.application.BaseEventStore;
import dev.ted.jitterticket.eventsourced.domain.EventSourcedAggregate;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethodWhere;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "dev.ted.jitterticket", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule onlyBaseEventStoreCanCallUncommittedEventsFromProductionCode =
            noClasses()
                    .that().areNotAssignableTo(BaseEventStore.class)
                    .should(callMethodWhere(
                            JavaCall.Predicates.target(HasName.Predicates.name("uncommittedEvents"))
                                    .and(JavaCall.Predicates.target(
                                            HasOwner.Predicates.With.owner(assignableTo(EventSourcedAggregate.class))
                                    ))
                    ));

}