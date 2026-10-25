# UserService 实现类本身：保留类名与全部成员（构造 + Stub 方法）。
-keep class com.cheku.app.carmode.SpectrumUserService { *; }

-keep class com.cheku.app.carmode.ISpectrumService { *; }
-keep class com.cheku.app.carmode.ISpectrumService$* { *; }
-keep class com.cheku.app.carmode.ISpectrumCallback { *; }
-keep class com.cheku.app.carmode.ISpectrumCallback$* { *; }

# Shizuku 侧：provider 在 Manifest 里按名声明，api 里的回调接口由 Shizuku 反射调用。
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

-keep class com.cheku.app.carmode.SpectrumFft { *; }
-keep class com.cheku.app.carmode.SpectrumAgc { *; }
