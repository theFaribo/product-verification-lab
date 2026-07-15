package kotlin.ru.example.productverification.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DecisionTest {

    @Test
    fun `should expose valid decision`() {
        assertEquals("VALID", Decision.VALID.name)
    }
}
