use serde::Deserialize;
use std::fs;
use std::path::Path;
use thiserror::Error;

const MAX_CONFIG_BYTES: u64 = 64 * 1024;
const MIN_INTERVAL_SECONDS: u64 = 5;
const MAX_INTERVAL_SECONDS: u64 = 300;

/// Explicit, versioned, fail-closed configuration. Do not add secret material
/// or silently enable collection based on an environment variable.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct AgentConfig {
    pub schema_version: u32,
    pub runtime: RuntimeConfig,
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct RuntimeConfig {
    pub cycle_seconds: u64,
}

#[derive(Debug, Error)]
pub enum ConfigError {
    #[error("agent configuration path must be absolute")]
    NonAbsolutePath,
    #[error("cannot read agent configuration")]
    Read(#[source] std::io::Error),
    #[error("agent configuration exceeds 64 KiB")]
    TooLarge,
    #[error("invalid agent TOML configuration")]
    Parse(#[source] toml::de::Error),
    #[error("unsupported config version or runtime interval")]
    InvalidPolicy,
}

impl AgentConfig {
    pub fn load(path: &Path) -> Result<Self, ConfigError> {
        if !path.is_absolute() {
            return Err(ConfigError::NonAbsolutePath);
        }
        let metadata = fs::metadata(path).map_err(ConfigError::Read)?;
        if !metadata.is_file() {
            return Err(ConfigError::InvalidPolicy);
        }
        if metadata.len() > MAX_CONFIG_BYTES {
            return Err(ConfigError::TooLarge);
        }
        let source = fs::read_to_string(path).map_err(ConfigError::Read)?;
        if source.len() as u64 > MAX_CONFIG_BYTES {
            return Err(ConfigError::TooLarge);
        }
        Self::parse(&source)
    }

    pub fn parse(text: &str) -> Result<Self, ConfigError> {
        if text.len() as u64 > MAX_CONFIG_BYTES {
            return Err(ConfigError::TooLarge);
        }
        let config: Self = toml::from_str(text).map_err(ConfigError::Parse)?;
        if config.schema_version != 1
            || !(MIN_INTERVAL_SECONDS..=MAX_INTERVAL_SECONDS)
                .contains(&config.runtime.cycle_seconds)
        {
            return Err(ConfigError::InvalidPolicy);
        }
        Ok(config)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const SYNTHETIC: &str = "schema_version = 1\n[runtime]\ncycle_seconds = 30\n";

    #[test]
    fn strict_opt_in_config_loads_without_hidden_collection_defaults() {
        let config = AgentConfig::parse(SYNTHETIC).unwrap();
        assert_eq!(config.schema_version, 1);
        assert_eq!(config.runtime.cycle_seconds, 30);
    }

    #[test]
    fn unknown_fields_and_extra_collectors_are_refused() {
        assert!(matches!(
            AgentConfig::parse(&format!("{SYNTHETIC}capture_enabled = true\n")),
            Err(ConfigError::Parse(_))
        ));
        assert!(matches!(
            AgentConfig::parse(&format!("{SYNTHETIC}[audit]\ncommands = true\n")),
            Err(ConfigError::Parse(_))
        ));
    }

    #[test]
    fn version_interval_and_missing_configuration_fail_closed() {
        for source in [
            "schema_version = 0\n[runtime]\ncycle_seconds = 30\n",
            "schema_version = 2\n[runtime]\ncycle_seconds = 30\n",
            "schema_version = 1\n[runtime]\ncycle_seconds = 0\n",
            "schema_version = 1\n[runtime]\ncycle_seconds = 301\n",
            "schema_version = 1\n[runtime]\ncycle_seconds = 4\n",
        ] {
            assert!(matches!(
                AgentConfig::parse(source),
                Err(ConfigError::InvalidPolicy)
            ));
        }
        assert!(AgentConfig::parse("").is_err());
    }

    #[test]
    fn explicit_absolute_file_required() {
        assert!(matches!(
            AgentConfig::load(Path::new("agent.toml")),
            Err(ConfigError::NonAbsolutePath)
        ));
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("agent.toml");
        fs::write(&file, SYNTHETIC).unwrap();
        assert_eq!(AgentConfig::load(&file).unwrap().runtime.cycle_seconds, 30);
    }

    #[test]
    fn oversized_configuration_rejected_before_parse() {
        assert!(matches!(
            AgentConfig::parse(&"x".repeat(65_537)),
            Err(ConfigError::TooLarge)
        ));
    }
}
