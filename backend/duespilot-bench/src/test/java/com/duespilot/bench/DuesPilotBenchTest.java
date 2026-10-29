package com.duespilot.bench;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuesPilotBenchTest {

    @Test
    void benchModuleIdentifiesCorrectly() {
        assertThat(DuesPilotBench.name()).isEqualTo("duespilot-bench");
    }
}
