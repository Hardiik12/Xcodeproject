//
//  LoginView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public struct LoginView: View {
    let onContinue: (String) -> Void
    let onCreateAccount: () -> Void
    let onContinueAsGuest: () -> Void
    let onBack: () -> Void
    
    @State private var input: String = ""
    @State private var validationError: String? = nil
    @FocusState private var isInputFocused: Bool
    
    public init(
        onContinue: @escaping (String) -> Void,
        onCreateAccount: @escaping () -> Void,
        onContinueAsGuest: @escaping () -> Void,
        onBack: @escaping () -> Void
    ) {
        self.onContinue = onContinue
        self.onCreateAccount = onCreateAccount
        self.onContinueAsGuest = onContinueAsGuest
        self.onBack = onBack
    }
    
    public var body: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header Bar (Back Button & Wordmark)
                headerBar
                
                ScrollView {
                    VStack(spacing: AppSpacing.large) {
                        Spacer().frame(height: 12)
                        
                        // Title & Subtitle Branding Header
                        VStack(spacing: AppSpacing.xSmall) {
                            Text("Welcome back")
                                .font(AppTypography.heroTitle)
                                .foregroundStyle(AppColors.textPrimary)
                            
                            Text("Continue your journey with CommunityOTT.")
                                .font(AppTypography.body)
                                .foregroundStyle(AppColors.textSecondary)
                                .multilineTextAlignment(.center)
                        }
                        
                        // Input Field Card Container
                        VStack(alignment: .leading, spacing: AppSpacing.xSmall) {
                            HStack {
                                Image(systemName: "envelope.fill")
                                    .font(.system(size: 16))
                                    .foregroundStyle(isInputFocused ? AppColors.primary : AppColors.textMuted)
                                
                                TextField("Enter email or mobile number", text: $input)
                                    .font(AppTypography.body)
                                    .foregroundStyle(AppColors.textPrimary)
                                    .keyboardType(.emailAddress)
                                    .autocapitalization(.none)
                                    .autocorrectionDisabled()
                                    .focused($isInputFocused)
                                    .onChange(of: input) { _ in
                                        if validationError != nil {
                                            validationError = nil
                                        }
                                    }
                                
                                if !input.isEmpty {
                                    Button {
                                        input = ""
                                        validationError = nil
                                    } label: {
                                        Image(systemName: "xmark.circle.fill")
                                            .font(.system(size: 16))
                                            .foregroundStyle(AppColors.textMuted)
                                    }
                                    .accessibilityLabel("Clear email or mobile number input")
                                }
                            }
                            .padding(AppSpacing.medium)
                            .background(AppColors.cardSurface)
                            .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
                            .overlay(
                                RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                                    .stroke(
                                        validationError != nil ? AppColors.error : (isInputFocused ? AppColors.primary : Color.white.opacity(0.12)),
                                        lineWidth: isInputFocused || validationError != nil ? 1.5 : 1
                                    )
                            )
                            
                            if let validationError {
                                Text(validationError)
                                    .font(AppTypography.caption)
                                    .foregroundStyle(AppColors.error)
                                    .padding(.leading, AppSpacing.xSmall)
                            }
                        }
                        .padding(.horizontal, AppSpacing.large)
                        
                        // Primary Action Button
                        PrimaryButton(
                            title: "Continue",
                            iconSystemName: "arrow.right"
                        ) {
                            validateAndSubmit()
                        }
                        .padding(.horizontal, AppSpacing.large)
                        .accessibilityLabel("Continue button")
                        
                        // Divider Line
                        HStack {
                            Rectangle()
                                .fill(Color.white.opacity(0.1))
                                .frame(height: 1)
                            Text("or")
                                .font(AppTypography.caption)
                                .foregroundStyle(AppColors.textMuted)
                            Rectangle()
                                .fill(Color.white.opacity(0.1))
                                .frame(height: 1)
                        }
                        .padding(.horizontal, AppSpacing.large)
                        .padding(.vertical, AppSpacing.xxSmall)
                        
                        // Social Login UI Placeholders
                        VStack(spacing: AppSpacing.small) {
                            socialButton(title: "Continue with Apple", iconSystemName: "applelogo")
                            socialButton(title: "Continue with Google", iconSystemName: "g.circle.fill")
                        }
                        .padding(.horizontal, AppSpacing.large)
                        
                        Spacer().frame(height: 12)
                        
                        // Footer Links: Register & Guest Access
                        VStack(spacing: AppSpacing.medium) {
                            Button {
                                onCreateAccount()
                            } label: {
                                HStack(spacing: 4) {
                                    Text("Don't have an account?")
                                        .font(AppTypography.subheadline)
                                        .foregroundStyle(AppColors.textSecondary)
                                    Text("Create Account")
                                        .font(AppTypography.headline)
                                        .foregroundStyle(AppColors.primary)
                                }
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Don't have an account? Create Account button")
                            
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
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Continue as Guest button")
                        }
                        .padding(.bottom, AppSpacing.large)
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
        .onTapGesture {
            isInputFocused = false
        }
    }
    
    private var headerBar: some View {
        HStack {
            Button(action: onBack) {
                HStack(spacing: 4) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16, weight: .bold))
                    Text("Back")
                        .font(AppTypography.subheadline)
                }
                .foregroundStyle(AppColors.primary)
            }
            .accessibilityLabel("Back to Landing screen")
            
            Spacer()
            
            HStack(spacing: 4) {
                Text("Community")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(AppColors.textPrimary)
                Text("OTT")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(AppColors.primary)
            }
        }
        .padding(.horizontal, AppSpacing.medium)
        .padding(.top, AppSpacing.small)
        .padding(.bottom, AppSpacing.xSmall)
    }
    
    private func socialButton(title: String, iconSystemName: String) -> some View {
        Button {
            // UI Placeholder action
        } label: {
            HStack(spacing: AppSpacing.small) {
                Image(systemName: iconSystemName)
                    .font(.system(size: 18))
                Text(title)
                    .font(AppTypography.subheadline)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(AppColors.cardSurface)
            .foregroundStyle(AppColors.textPrimary)
            .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
            .overlay(
                RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
            )
        }
        .buttonStyle(.cardPress)
        .accessibilityLabel(title)
    }
    
    private func validateAndSubmit() {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            validationError = "Please enter your email or mobile number."
            return
        }
        
        let isEmail = trimmed.contains("@") && trimmed.contains(".")
        let isPhone = trimmed.allSatisfy { $0.isNumber || $0 == "+" || $0 == "-" || $0 == " " } && trimmed.count >= 7
        
        if !isEmail && !isPhone {
            validationError = "Please enter a valid email or mobile number."
            return
        }
        
        validationError = nil
        onContinue(trimmed)
    }
}

#Preview {
    LoginView(
        onContinue: { _ in },
        onCreateAccount: {},
        onContinueAsGuest: {},
        onBack: {}
    )
}
