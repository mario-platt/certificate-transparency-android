/*
 * Copyright 2019 Babylon Partners Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.babylon.certificatetransparency.chaincleaner

import com.babylon.certificatetransparency.utils.TestData
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.X509TrustManager


class BasicCertificateChainCleanerTest {

    @Test(expected = SSLPeerUnverifiedException::class)
    fun noLeafCertificateInChainThrowsException() {
        // given a basic chain cleaner
        val chainCleaner = certificateChainCleanerWithRootCert()

        // when we clean an empty certificate chain
        chainCleaner.clean(emptyList(), "127.0.0.1")

        // then an exception is thrown
    }

    private fun certificateChainCleanerWithRootCert(rootCert: X509Certificate? = null): CertificateChainCleaner {
        val certs = rootCert?.let { listOf(it) } ?: TestData.loadCertificates(TestData.ROOT_CA_CERT)

        val trustManager = mock<X509TrustManager>().apply {
            whenever(acceptedIssuers)
                .thenReturn(certs.toTypedArray())
        }
        return BasicCertificateChainCleaner(trustManager)
    }

    private fun certificateChain(certPaths: List<String>): List<X509Certificate> {
        return certPaths.flatMap(TestData::loadCertificates)
    }

    private val expectedValidChain = listOf(
        TestData.TEST_PRE_CERT_SIGNED_BY_PRECA_INTERMEDIATE,
        TestData.PRE_CERT_SIGNING_BY_INTERMEDIATE,
        TestData.INTERMEDIATE_CA_CERT,
        TestData.ROOT_CA_CERT
    ).flatMap(TestData::loadCertificates)

    @Test
    fun cleaningValidChainReturnsSuccessfully() {
        // given a basic chain cleaner
        val chainCleaner = certificateChainCleanerWithRootCert()

        // when we clean a valid chain
        val certsChain = certificateChain(
            listOf(
                TestData.TEST_PRE_CERT_SIGNED_BY_PRECA_INTERMEDIATE,
                TestData.PRE_CERT_SIGNING_BY_INTERMEDIATE,
                TestData.INTERMEDIATE_CA_CERT
            )
        )
        val cleanedChain = chainCleaner.clean(certsChain, "127.0.0.1")

        // then the expected chain is returned
        assertEquals(expectedValidChain, cleanedChain)
    }

    @Test(expected = SSLPeerUnverifiedException::class)
    fun cleaningIncompleteChainThrowsException() {
        // given a basic chain cleaner
        val chainCleaner = certificateChainCleanerWithRootCert()

        // when we clean a chain with missing certs (TestData.PRE_CERT_SIGNING_BY_INTERMEDIATE)
        val certsChain = certificateChain(
            listOf(
                TestData.TEST_PRE_CERT_SIGNED_BY_PRECA_INTERMEDIATE,
                TestData.INTERMEDIATE_CA_CERT
            )
        )
        chainCleaner.clean(certsChain, "127.0.0.1")

        // then an exception is thrown
    }

    @Test
    fun cleaningOutOfOrderChainReturnsSuccessfully() {
        // given a basic chain cleaner
        val chainCleaner = certificateChainCleanerWithRootCert()

        // when we clean a valid chain
        val certsChain = certificateChain(
            listOf(
                TestData.TEST_PRE_CERT_SIGNED_BY_PRECA_INTERMEDIATE,
                TestData.INTERMEDIATE_CA_CERT,
                TestData.PRE_CERT_SIGNING_BY_INTERMEDIATE
            )
        )
        val cleanedChain = chainCleaner.clean(certsChain, "127.0.0.1")

        // then the expected chain is returned
        assertEquals(expectedValidChain, cleanedChain)
    }

    @Test
    fun cleaningChainWithExtraCertsReturnsSuccessfully() {
        // given a basic chain cleaner
        val chainCleaner = certificateChainCleanerWithRootCert()

        // when we clean a valid chain
        val certsChain = certificateChain(
            listOf(
                TestData.TEST_PRE_CERT_SIGNED_BY_PRECA_INTERMEDIATE,

                // unnecessary certs
                TestData.TEST_PRE_CERT,
                TestData.TEST_CERT,
                TestData.TEST_INTERMEDIATE_CERT,
                TestData.PRE_CERT_SIGNING_BY_INTERMEDIATE,

                TestData.INTERMEDIATE_CA_CERT
            )
        )
        val cleanedChain = chainCleaner.clean(certsChain, "127.0.0.1")

        // then the expected chain is returned
        assertEquals(expectedValidChain, cleanedChain)
    }

    @Test(expected = SSLPeerUnverifiedException::class)
    fun cleaningChainWithOnlyLeafThrowsException() {
        // given a basic chain cleaner
        val chainCleaner = certificateChainCleanerWithRootCert()

        // when we clean a valid chain
        val certsChain = certificateChain(listOf(TestData.TEST_PRE_CERT_SIGNED_BY_PRECA_INTERMEDIATE))
        val cleanedChain = chainCleaner.clean(certsChain, "127.0.0.1")

        // then the expected chain is returned
        assertEquals(expectedValidChain, cleanedChain)
    }


    @Test
    fun largeValidChainReturnsSuccessfully() {
        val rootCert = TestData.loadCertificates(TestData.TEN_CERTS_ROOT_CERT)[0]
        val certsChain = TestData.loadCertificates(TestData.TEN_CERTS_CHAIN)

        // given a basic chain cleaner
        val chainCleaner = certificateChainCleanerWithRootCert(rootCert)

        // when we clean a chain of exactly 10 certs
        val cleanedChain = chainCleaner.clean(certsChain, "127.0.0.1")

        // then the expected chain is returned
        assertEquals(certsChain + rootCert, cleanedChain)
    }

    @Test
    fun trustedCertInMiddleOfChainReturnsSuccessfully() {
        val certsChain =
            TestData.loadCertificates(TestData.TEN_CERTS_CHAIN)
        val trustedCert = certsChain[5]

        // given a basic chain cleaner
        val chainCleaner = certificateChainCleanerWithRootCert(trustedCert)

        // when we clean a chain of exactly 10 certs
        val cleanedChain = chainCleaner.clean(certsChain, "127.0.0.1")

        // then the expected chain is returned
        assertEquals(certsChain, cleanedChain)
    }

    @Test(expected = SSLPeerUnverifiedException::class)
    fun reallyLargeValidChainThrowsException() {
        val rootCert = TestData.loadCertificates(TestData.ELEVEN_CERTS_ROOT_CERT)[0]
        val certsChain = TestData.loadCertificates(TestData.ELEVEN_CERTS_CHAIN)

        // given a basic chain cleaner
        val chainCleaner = certificateChainCleanerWithRootCert(rootCert)

        // when we clean a chain with more than 10 certs (inc root)
        chainCleaner.clean(certsChain, "127.0.0.1")

        // then an exception is thrown
    }

    @Test
    fun trustedSelfSignedRootCertReturnsSuccessfully() {
        val rootCert = TestData.loadCertificates(TestData.SELF_SIGNED_ROOT_CERT)[0]

        // given a basic chain cleaner
        val chainCleaner = certificateChainCleanerWithRootCert(rootCert)

        // when we clean a chain of the self-signed root cert
        val cleanedChain = chainCleaner.clean(listOf(rootCert), "127.0.0.1")

        // then the expected chain is returned
        assertEquals(listOf(rootCert), cleanedChain)
    }
}