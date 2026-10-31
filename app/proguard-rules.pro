# JSch uses reflection for some key/cipher implementations.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
