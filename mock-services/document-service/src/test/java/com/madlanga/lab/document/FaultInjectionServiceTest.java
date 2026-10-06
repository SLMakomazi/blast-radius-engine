package com.madlanga.lab.document;

import com.madlanga.lab.document.fault.FaultConfig;
import com.madlanga.lab.document.fault.FaultInjectionService;
import com.madlanga.lab.document.fault.FaultMode;
import com.madlanga.lab.document.fault.SyntheticFaultException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaultInjectionServiceTest {

    private final FaultInjectionService faults = new FaultInjectionService();

    @Test
    void error500FailsWhileServiceProcessRemainsAlive() {
        faults.configure(new FaultConfig(FaultMode.ERROR_500, 0, 5));

        assertThatThrownBy(faults::beforeDocumentRequest)
                .isInstanceOf(SyntheticFaultException.class);
    }

    @Test
    void intermittentModeFailsOnlyEveryConfiguredRequest() {
        faults.configure(new FaultConfig(FaultMode.INTERMITTENT_500, 0, 3));

        faults.beforeDocumentRequest();
        faults.beforeDocumentRequest();
        assertThatThrownBy(faults::beforeDocumentRequest)
                .isInstanceOf(SyntheticFaultException.class);
        faults.beforeDocumentRequest();
    }

    @Test
    void latencyModeAddsBoundedDelayWithoutStoppingService() {
        faults.configure(new FaultConfig(FaultMode.LATENCY, 50, 5));

        long started = System.nanoTime();
        faults.beforeDocumentRequest();
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(40);
    }

    @Test
    void databaseFailureLooksLikeConnectivityFailureToExistingHandler() {
        faults.configure(new FaultConfig(FaultMode.DATABASE_FAILURE, 0, 5));

        assertThatThrownBy(faults::beforeDocumentRequest)
                .isInstanceOf(CannotGetJdbcConnectionException.class);
    }

    @Test
    void deploymentRegressionFailsWithoutStoppingService() {
        faults.configure(new FaultConfig(FaultMode.DEPLOYMENT_REGRESSION, 0, 5));
        assertThatThrownBy(faults::beforeDocumentRequest).isInstanceOf(SyntheticFaultException.class);
    }

    @Test
    void configurationErrorFailsWithoutStoppingService() {
        faults.configure(new FaultConfig(FaultMode.CONFIGURATION_ERROR, 0, 5));
        assertThatThrownBy(faults::beforeDocumentRequest).isInstanceOf(SyntheticFaultException.class);
    }

    @Test
    void contractBreakFailsWithoutStoppingService() {
        faults.configure(new FaultConfig(FaultMode.CONTRACT_BREAK, 0, 5));
        assertThatThrownBy(faults::beforeDocumentRequest).isInstanceOf(SyntheticFaultException.class);
    }

    @Test
    void featureFlagRegressionIsIntermittentAndReversible() {
        faults.configure(new FaultConfig(FaultMode.FEATURE_FLAG_REGRESSION, 0, 2));
        faults.beforeDocumentRequest();
        assertThatThrownBy(faults::beforeDocumentRequest).isInstanceOf(SyntheticFaultException.class);
        faults.reset();
        faults.beforeDocumentRequest();
    }

    @Test
    void distributedCascadeFailsWithoutStoppingService() {
        faults.configure(new FaultConfig(FaultMode.DISTRIBUTED_CASCADE, 0, 5));
        assertThatThrownBy(faults::beforeDocumentRequest).isInstanceOf(SyntheticFaultException.class);
    }

    @Test
    void compoundDependencyFailureLooksLikeDatabaseFailure() {
        faults.configure(new FaultConfig(FaultMode.COMPOUND_DEPENDENCY_FAILURE, 0, 5));
        assertThatThrownBy(faults::beforeDocumentRequest)
                .isInstanceOf(CannotGetJdbcConnectionException.class);
    }

    @Test
    void flappingDependencyFailsAtConfiguredCadenceAndRecovers() {
        faults.configure(new FaultConfig(FaultMode.FLAPPING_DEPENDENCY, 0, 2));
        faults.beforeDocumentRequest();
        assertThatThrownBy(faults::beforeDocumentRequest).isInstanceOf(SyntheticFaultException.class);
        faults.beforeDocumentRequest();
        faults.reset();
        faults.beforeDocumentRequest();
    }

    @Test
    void resetReturnsToHealthyMode() {
        faults.configure(new FaultConfig(FaultMode.ERROR_500, 0, 5));
        FaultConfig reset = faults.reset();

        assertThat(reset.mode()).isEqualTo(FaultMode.NONE);
        faults.beforeDocumentRequest();
    }

    @Test
    void rejectsUnboundedLatency() {
        assertThatThrownBy(() -> new FaultConfig(FaultMode.LATENCY, 15_001, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
