class geowave::app {

  $geowave_base_app_rpms = [
    "geowave-docs",
    "geowave-${geowave::hadoop_vendor_version}-ingest",
  ]

  package { $geowave_base_app_rpms:
    ensure => latest,
    tag    => 'geowave-package',
  }

  if !defined(Package["geowave-core"]) {
    package { "geowave-core":
      ensure => latest,
      tag    => 'geowave-package',
    }
  }

}
