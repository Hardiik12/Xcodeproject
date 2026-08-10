//
//  LandingView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public struct LandingView: View {
    @ObservedObject private var langStore = LanguagePreferenceStore.shared
    
    let onGetStarted: () -> Void
    let onSignIn: () -> Void
    let onContinueAsGuest: () -> Void
    
    @State private var isLoaded = false
    
    public init(
        onGetStarted: @escaping () -> Void,
        onSignIn: @escaping () -> Void,
        onContinueAsGuest: @escaping () -> Void
    ) {
        self.onGetStarted = onGetStarted
        self.onSignIn = onSignIn
        self.onContinueAsGuest = onContinueAsGuest
    }
    
    public var body: some View {
        ZStack(alignment: .bottom) {
            AppColors.background.ignoresSafeArea()
            
            // Full-Screen Cultural Background Image & Gradient Vignette
            ZStack(alignment: .bottom) {
                Image("hero_heritage")
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
                    .opacity(isLoaded ? 0.45 : 0.0)
                
                LinearGradient(
                    colors: [
                        AppColors.background.opacity(0.3),
                        AppColors.background.opacity(0.7),
                        AppColors.background.opacity(0.95),
                        AppColors.background
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
            .ignoresSafeArea()
            
            // Content Stack
            VStack(spacing: 0) {
                Spacer()
                
                // Branding Header & Tagline
                VStack(spacing: AppSpacing.small) {
                    HStack(spacing: 4) {
                        Text("Community")
                            .font(.system(size: 36, weight: .bold))
                            .foregroundStyle(AppColors.textPrimary)
                        
                        Text("OTT")
                            .font(.system(size: 36, weight: .bold))
                            .foregroundStyle(AppColors.primary)
                    }
                    .accessibilityLabel("CommunityOTT")
                    
                    VStack(spacing: 2) {
                        Text("Our Story.")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundStyle(AppColors.primary)
                        Text("Our Stage. Our Future.")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundStyle(AppColors.textPrimary)
                    }
                    .multilineTextAlignment(.center)
                    
                    Text("Stories, culture, achievements and voices from our community.")
                        .font(AppTypography.body)
                        .foregroundStyle(AppColors.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, AppSpacing.large)
                        .padding(.top, AppSpacing.xxSmall)
                }
                .opacity(isLoaded ? 1.0 : 0.0)
                .offset(y: isLoaded ? 0 : 20)
                
                Spacer().frame(height: 36)
                
                // Primary & Secondary Action CTAs
                VStack(spacing: AppSpacing.medium) {
                    // Primary Gold CTA: Get Started
                    PrimaryButton(
                        title: "Get Started",
                        iconSystemName: "arrow.right"
                    ) {
                        onGetStarted()
                    }
                    .accessibilityLabel("Get Started button")
                    
                    // Already a member? Sign In
                    Button {
                        onSignIn()
                    } label: {
                        HStack(spacing: 4) {
                            Text("Already a member?")
                                .font(AppTypography.subheadline)
                                .foregroundStyle(AppColors.textSecondary)
                            Text("Sign In")
                                .font(AppTypography.headline)
                                .foregroundStyle(AppColors.primary)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Already a member? Sign In button")
                    
                    // Guest Access: Continue as Guest
                    Button {
                        onContinueAsGuest()
                    } label: {
                        HStack(spacing: AppSpacing.xxSmall + 2) {
                            Image(systemName: "eye.fill")
                                .font(.system(size: 14))
                            Text("Continue as Guest")
                                .font(AppTypography.subheadline)
                        }
                        .foregroundStyle(AppColors.textSecondary)
                        .padding(.vertical, AppSpacing.xxSmall)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Continue as Guest button")
                }
                .padding(.horizontal, AppSpacing.large)
                .opacity(isLoaded ? 1.0 : 0.0)
                .offset(y: isLoaded ? 0 : 30)
                
                Spacer().frame(height: 28)
                
                // Bottom Subtitle Language Switcher (English | తెలుగు)
                HStack(spacing: AppSpacing.medium) {
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            langStore.setLanguage(.english)
                        }
                    } label: {
                        Text("English")
                            .font(AppTypography.caption)
                            .foregroundStyle(langStore.currentLanguage == .english ? AppColors.primary : AppColors.textMuted)
                            .fontWeight(langStore.currentLanguage == .english ? .bold : .regular)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Switch interface language to English")
                    
                    Text("|")
                        .font(AppTypography.caption)
                        .foregroundStyle(AppColors.textMuted.opacity(0.4))
                    
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            langStore.setLanguage(.telugu)
                        }
                    } label: {
                        Text("తెలుగు")
                            .font(AppTypography.caption)
                            .foregroundStyle(langStore.currentLanguage == .telugu ? AppColors.primary : AppColors.textMuted)
                            .fontWeight(langStore.currentLanguage == .telugu ? .bold : .regular)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Switch interface language to Telugu")
                }
                .padding(.bottom, AppSpacing.medium)
                .opacity(isLoaded ? 1.0 : 0.0)
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            withAnimation(.easeOut(duration: 0.6)) {
                isLoaded = true
            }
        }
    }
}

#Preview {
    LandingView(
        onGetStarted: {},
        onSignIn: {},
        onContinueAsGuest: {}
    )
}
