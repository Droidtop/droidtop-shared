// Test-only: builds throwaway X.509 chains and JWS signatures for the tests of the certificate
// signature format. Never a dependency of anything shipped; every key it makes lives only in a
// test run.
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    // Conscrypt is the provider on a device; the verifier's tests run under it too.
    api("org.conscrypt:conscrypt-openjdk-uber:2.5.2")
}
