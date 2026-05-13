package com.connecthub.media.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaExceptionsTest {

    @Test
    void mediaPlanLimitException_hasMessage() {
        MediaPlanLimitException ex = new MediaPlanLimitException("limit reached");
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("limit reached");
    }

    @Test
    void mediaStorageQuotaException_hasMessage() {
        MediaStorageQuotaException ex = new MediaStorageQuotaException("quota exceeded");
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("quota exceeded");
    }
}
