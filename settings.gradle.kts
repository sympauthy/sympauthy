// Make sure project name is set to sympauthy when building inside Docker.
rootProject.name = "sympauthy"

include("server", "data", "common")
include("data-postgresql")
include("data-h2")
