import AnchorShared
import SwiftUI

extension Color {
    /// Creates a color from a 32-bit ARGB value (as stored in the shared `AccentColorOption`).
    init(argb: Int64) {
        let value = UInt32(truncatingIfNeeded: argb)
        self.init(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255.0,
            green: Double((value >> 8) & 0xFF) / 255.0,
            blue: Double(value & 0xFF) / 255.0,
            opacity: Double((value >> 24) & 0xFF) / 255.0
        )
    }
}

enum AnchorPalette {
    static let accentDownbeat = Color(argb: 0xFFFF_B300)
    static let accentMute = Color.gray.opacity(0.35)
}

extension AccentColorOption {
    /// "Phone Theme" maps to the iOS system tint; curated palettes use their ARGB value.
    var swiftUIColor: Color {
        isDynamic ? Color.accentColor : Color(argb: argbHex)
    }
}

extension ThemeMode {
    var colorScheme: ColorScheme? {
        switch name {
        case "DARK": return .dark
        case "LIGHT": return .light
        default: return nil
        }
    }
}

extension BeatAccent {
    /// Relative bar height for the beat indicator.
    var level: CGFloat {
        switch name {
        case "ACCENT": return 1.0
        case "REGULAR": return 0.7
        case "SUB": return 0.45
        default: return 0.18
        }
    }

    var isMuted: Bool { name == "MUTE" }
    var isAccent: Bool { name == "ACCENT" }
}

extension ButtonTriggerMode {
    var registersOnPress: Bool { name == "ON_PRESS" }
}
