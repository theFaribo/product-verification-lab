pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
// FAIL_ON_PROJECT_REPOS запрещает отдельным модулям самовольно добавлять репозитории.
// Это снижает риск случайного скачивания зависимостей из разных источников.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
    }
}

rootProject.name = "product-verification-lab"

include(
    "domain",
    "db-migrations",
    "mvc-app",
    "reactive-app",
    "stubs-app",
)
