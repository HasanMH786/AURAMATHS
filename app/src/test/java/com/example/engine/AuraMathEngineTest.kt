package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraMathEngineTest {

    @Test
    fun variableCascadingEvaluatesHypotenuse() {
        val (results, env) = AuraMathEngine.evaluateSheet(
            listOf(
                "a = 3",
                "b = 4",
                "Hypotenuse = sqrt(a^2 + b^2)"
            )
        )
        assertEquals("3", results[0].result)
        assertEquals("4", results[1].result)
        assertEquals("5", results[2].result)
        assertEquals(5.0, env["Hypotenuse"] ?: 0.0, 1e-6)
    }

    @Test
    fun symbolicCalculusDerivativesAndIntegrals() {
        val d1 = AuraMathEngine.evaluate("d/dx(x^3) =")
        assertTrue("Expected 3x^2 in ${d1.result}", d1.result.contains("3") && d1.result.contains("x^2"))

        val d2 = AuraMathEngine.evaluate("d^2/dx^2(x^3 + 2x) =")
        assertTrue("Expected 6x in ${d2.result}", d2.result.contains("6x"))

        val defInt = AuraMathEngine.evaluate("integrate(x^2, 0, 3) =")
        assertEquals("9", defInt.result)
    }

    @Test
    fun linearAlgebraMatricesUpTo4x4() {
        val detRes = AuraMathEngine.evaluate("det([2, 3; 1, 4]) =")
        assertEquals("5", detRes.result)

        val invRes = AuraMathEngine.evaluate("inv([4, 7; 2, 6]) =")
        assertTrue(invRes.result.contains("0.6"))

        val eigenRes = AuraMathEngine.evaluate("eigen([2, 1; 1, 2]) =")
        assertTrue(eigenRes.result.contains("3") && eigenRes.result.contains("1"))
    }

    @Test
    fun discreteMathBasesBooleanAndComplex() {
        val baseRes = AuraMathEngine.evaluate("(1011)_2 + (F)_16 =")
        assertTrue(baseRes.result.startsWith("26"))
        assertTrue(baseRes.result.contains("0x1A"))

        val boolRes = AuraMathEngine.evaluate("truth(A && B || !A) =")
        assertTrue(boolRes.result.contains("SOP"))
        assertTrue(boolRes.steps.any { it.contains("||") })

        val polarRes = AuraMathEngine.evaluate("polar(3 + 4i) =")
        assertTrue(polarRes.result.startsWith("5∠"))
    }

    @Test
    fun graphingDetects2DAnd3DSurfaces() {
        val g2 = AuraMathEngine.evaluate("y = x^2 - 4x + 3")
        assertNotNull(g2.graphSpec)
        assertEquals(false, g2.graphSpec!!.is3D)
        assertEquals(2, g2.graphSpec!!.roots.size)

        val g3 = AuraMathEngine.evaluate("z = sin(x) * cos(y)")
        assertNotNull(g3.graphSpec)
        assertEquals(true, g3.graphSpec!!.is3D)
    }
}
