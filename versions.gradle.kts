fun getVersionNameFromCode(code: Int): String {
    val major = code / 10000
    val minor = (code % 10000) / 100
    val patch = code % 100
    return "$major.$minor.$patch"
}

val compileSdkVersion = 35
val minSdkVersion = 29
val targetSdkVersion = 35

val baseNuguVersionCode = 20000
val baseNuguVersionName = "2.0.0"

var nuguVersionCode: Int
var nuguVersionName: String

if (rootProject.extra.has("PUBLISH_SNAPSHOT") && rootProject.extra["PUBLISH_SNAPSHOT"] as Boolean) {
    val timestamp = System.getenv("SNAPSHOT_TIMESTAMP") ?: "unknown"
    nuguVersionCode = baseNuguVersionCode + 1
    nuguVersionName = "${getVersionNameFromCode(nuguVersionCode)}-$timestamp-SNAPSHOT"
} else {
    nuguVersionCode = baseNuguVersionCode
    nuguVersionName = getVersionNameFromCode(nuguVersionCode)
}

extra["compileSdkVersion"] = compileSdkVersion
extra["minSdkVersion"] = minSdkVersion
extra["targetSdkVersion"] = targetSdkVersion
extra["nuguVersionCode"] = nuguVersionCode
extra["nuguVersionName"] = nuguVersionName
