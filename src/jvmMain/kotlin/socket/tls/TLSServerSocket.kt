package socket.tls

import mqtt.broker.Broker
import socket.ServerSocket
import socket.tcp.Socket
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext


actual class TLSServerSocket actual constructor(private val broker: Broker) : ServerSocket(broker) {

    private val sslContext = SSLContext.getInstance(broker.tlsSettings!!.version)
    private var sendAppBuffer: ByteBuffer
    private var receiveAppBuffer: ByteBuffer

    init {
        val keyStore = KeyStore.getInstance("PKCS12")
        File(broker.tlsSettings!!.keyStoreFilePath).inputStream().use {
            keyStore.load(it, broker.tlsSettings.keyStorePassword?.toCharArray())
        }
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, broker.tlsSettings.keyStorePassword?.toCharArray())

        sslContext.init(keyManagerFactory.keyManagers, null, null)

        val initSession = sslContext.createSSLEngine().session
        sendBuffer = ByteBuffer.allocate(initSession.packetBufferSize)
        receiveBuffer = ByteBuffer.allocate(initSession.packetBufferSize)
        sendAppBuffer = ByteBuffer.allocate(initSession.applicationBufferSize)
        receiveAppBuffer = ByteBuffer.allocate(initSession.applicationBufferSize)
        initSession.invalidate()
    }

    private fun buildKeyManagers(
        certificateBytes: ByteArray,
        keyAlgorithm: String,
        privateKeyBytes: ByteArray,
        publicKeyBytes: ByteArray,
        privateKeyPassword: String?
    ): Array<KeyManager> {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null)
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificate = certificateFactory.generateCertificate(ByteArrayInputStream(certificateBytes))

        val keyFactory = KeyFactory.getInstance(keyAlgorithm)
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
        certificate.verify(publicKey)

        keyStore.setCertificateEntry("main", certificate)
        keyStore.setKeyEntry("main", privateKey, privateKeyPassword?.toCharArray(), arrayOf(certificate))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, null)
        return kmf.keyManagers
    }

    override fun createSocket(socketKey: SelectionKey): Socket {
        val engine = sslContext.createSSLEngine()
        engine.useClientMode = false
        if (broker.tlsSettings?.requireClientCertificate == true) {
            engine.needClientAuth = true
        }
        engine.beginHandshake()
        return TLSSocket(socketKey, sendBuffer, receiveBuffer, sendAppBuffer, receiveAppBuffer, engine)
    }
}
