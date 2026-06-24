# sshj resolves ciphers/MACs/KEX factories by reflection from named classes.
-keep class net.schmizz.sshj.** { *; }
-dontwarn net.schmizz.sshj.**
-keep class com.hierynomus.** { *; }
-dontwarn com.hierynomus.**

# BouncyCastle providers are loaded by FQCN; the SDK ships a stripped BC that we
# replace with the full one in MainActivity, so both shipped classes must survive.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# sshj logs via SLF4J; we don't ship a binding, suppress so R8 doesn't fail.
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**

# ML Kit / Google code scanner: R8 strips the ComponentRegistrar no-arg constructors,
# breaking ML Kit init (NoSuchMethodException ...Registrar.<init>) and the QR scanner.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**
