//
//  AppColors.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public enum AppColors {
    // MARK: - Core Palette
    /// Dark cinematic background color (#0B0B0F)
    public static let background = Color(hex: 0x0B0B0F)
    
    /// Primary brand gold color (#D6A84F)
    public static let primary = Color(hex: 0xD6A84F)
    
    /// Secondary rich maroon accent color (#641F2B)
    public static let secondary = Color(hex: 0x641F2B)
    
    /// Dark surface card background color (#18181D)
    public static let cardSurface = Color(hex: 0x18181D)
    
    /// Elevated surface card color (#24242A)
    public static let elevatedCard = Color(hex: 0x24242A)
    
    // MARK: - Text Palette
    public static let textPrimary = Color.white
    public static let textSecondary = Color(hex: 0x9CA3AF)
    public static let textMuted = Color(hex: 0x6B7280)
    
    // MARK: - Semantic Palette
    public static let success = Color(hex: 0x10B981)
    public static let warning = Color(hex: 0xF59E0B)
    public static let error = Color(hex: 0xEF4444)
    
    // MARK: - Gradients
    public static let heroGradient = LinearGradient(
        colors: [Color.black.opacity(0.8), Color.clear, Color.black.opacity(0.95)],
        startPoint: .top,
        endPoint: .bottom
    )
    
    public static let primaryGradient = LinearGradient(
        colors: [Color(hex: 0xD6A84F), Color(hex: 0xB88B35)],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
}

extension Color {
    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xff) / 255.0,
            green: Double((hex >> 8) & 0xff) / 255.0,
            blue: Double(hex & 0xff) / 255.0,
            opacity: alpha
        )
    }
}
