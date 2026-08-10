//
//  LanguageSelectionView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public struct LanguageSelectionView: View {
    @ObservedObject private var langStore = LanguagePreferenceStore.shared
    
    public init() {}
    
    public var body: some View {
        VStack(spacing: AppSpacing.large) {
            VStack(spacing: AppSpacing.xxSmall) {
                Text("Choose your language")
                    .font(AppTypography.title2)
                    .foregroundStyle(AppColors.textPrimary)
                
                Text("Select your preferred language for the CommunityOTT interface.")
                    .font(AppTypography.body)
                    .foregroundStyle(AppColors.textSecondary)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, AppSpacing.medium)
            
            VStack(spacing: AppSpacing.medium) {
                ForEach(AppLanguage.allCases) { lang in
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            langStore.setLanguage(lang)
                        }
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(lang.displayName)
                                    .font(AppTypography.headline)
                                    .foregroundStyle(langStore.currentLanguage == lang ? AppColors.textPrimary : AppColors.textSecondary)
                                
                                Text(lang == .english ? "English Interface" : "తెలుగు ఇంటర్‌ఫేస్")
                                    .font(AppTypography.caption)
                                    .foregroundStyle(AppColors.textMuted)
                            }
                            
                            Spacer()
                            
                            if langStore.currentLanguage == lang {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 22))
                                    .foregroundStyle(AppColors.primary)
                            } else {
                                Circle()
                                    .stroke(Color.white.opacity(0.2), lineWidth: 1.5)
                                    .frame(width: 22, height: 22)
                            }
                        }
                        .padding(AppSpacing.medium)
                        .background(langStore.currentLanguage == lang ? AppColors.cardSurface : AppColors.cardSurface.opacity(0.5))
                        .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
                        .overlay(
                            RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                                .stroke(langStore.currentLanguage == lang ? AppColors.primary : Color.white.opacity(0.1), lineWidth: 1.5)
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Select language \(lang.displayName)")
                }
            }
            .padding(.horizontal, AppSpacing.medium)
        }
    }
}
