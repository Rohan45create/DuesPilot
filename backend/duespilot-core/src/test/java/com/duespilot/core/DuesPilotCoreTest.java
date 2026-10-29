package com.duespilot.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuesPilotCoreTest {

    @Test
    void coreModuleIdentifiesCorrectly() {
        assertThat(DuesPilotCore.name()).isEqualTo("duespilot-core");
    }
}
