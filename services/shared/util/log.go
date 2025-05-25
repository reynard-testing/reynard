package util

import (
	"log/slog"
	"os"
	"strings"
)

func ParseLogLevel(env string) slog.Level {
	switch strings.ToLower(env) {
	case "debug":
		return slog.LevelDebug
	case "info":
		return slog.LevelInfo
	case "warn", "warning":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo // Default level
	}
}

func GetLogLevel() slog.Level {
	logLevel := os.Getenv("LOG_LEVEL")
	return ParseLogLevel(logLevel)
}
