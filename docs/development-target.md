# Development target

The initial compatibility target is Ghidra 12.1.4 PUBLIC.

```text
Runtime installation: $HOME/ghidra/current
Optional source checkout: $HOME/git/ghidra-eclipsedev
Skeleton template: $HOME/git/ghidra-eclipsedev/GhidraBuild/Skeleton
```

Expected Ghidra metadata:

```properties
application.version=12.1.4
application.release.name=PUBLIC
application.java.compiler=21
application.java.min=21
application.gradle.min=8.5
```

The plugin must discover these values dynamically from:

```text
<Ghidra installation>/Ghidra/application.properties
```

Do not hardcode the initial target values in production code.
