//
//  RegisterView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public struct RegisterView: View {
    let onRegister: (String, String) -> Void
    let onBack: () -> Void
    
    @State private var fullName: String = ""
    @State private var email: String = ""
    @State private var mobileNumber: String = ""
    
    @State private var nameError: String? = nil
    @State private var emailError: String? = nil
    @State private var mobileError: String? = nil
    
    @FocusState private var activeField: Field?
    
    private enum Field {
        case name, email, mobile
    }
    
    public init(onRegister: @escaping (String, String) -> Void, onBack: @escaping () -> Void) {
        self.onRegister = onRegister
        self.onBack = onBack
    }
    
    public var body: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header Bar (Back Button & Wordmark Logo)
                headerBar
                
                ScrollView {
                    VStack(spacing: AppSpacing.large) {
                        Spacer().frame(height: 8)
                        
                        // Title & Subtitle Branding Header
                        VStack(spacing: AppSpacing.xSmall) {
                            Text("Create your account")
                                .font(AppTypography.heroTitle)
                                .foregroundStyle(AppColors.textPrimary)
                            
                            Text("Join CommunityOTT and discover stories, culture, achievements and community voices.")
                                .font(AppTypography.body)
                                .foregroundStyle(AppColors.textSecondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, AppSpacing.small)
                        }
                        
                        // Form Input Cards Container
                        VStack(spacing: AppSpacing.medium) {
                            // Full Name Input Card
                            inputCard(
                                iconSystemName: "person.fill",
                                placeholder: "Enter your full name",
                                text: $fullName,
                                error: nameError,
                                fieldType: .name,
                                keyboardType: .default,
                                autoCapitalize: .words
                            )
                            
                            // Email Input Card
                            inputCard(
                                iconSystemName: "envelope.fill",
                                placeholder: "Enter your email address",
                                text: $email,
                                error: emailError,
                                fieldType: .email,
                                keyboardType: .emailAddress,
                                autoCapitalize: .never
                            )
                            
                            // Mobile Number Input Card
                            inputCard(
                                iconSystemName: "phone.fill",
                                placeholder: "Enter your mobile number",
                                text: $mobileNumber,
                                error: mobileError,
                                fieldType: .mobile,
                                keyboardType: .phonePad,
                                autoCapitalize: .never
                            )
                        }
                        .padding(.horizontal, AppSpacing.large)
                        
                        // Primary Action CTA Button
                        PrimaryButton(
                            title: "Create Account",
                            iconSystemName: "arrow.right"
                        ) {
                            validateAndSubmit()
                        }
                        .padding(.horizontal, AppSpacing.large)
                        .accessibilityLabel("Create Account button")
                        
                        Spacer().frame(height: 8)
                        
                        // Footer Link: Already have an account? Sign In
                        Button {
                            onBack()
                        } label: {
                            HStack(spacing: 4) {
                                Text("Already have an account?")
                                    .font(AppTypography.subheadline)
                                    .foregroundStyle(AppColors.textSecondary)
                                Text("Sign In")
                                    .font(AppTypography.headline)
                                    .foregroundStyle(AppColors.primary)
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Already have an account? Sign In button")
                        .padding(.bottom, AppSpacing.large)
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
        .onTapGesture {
            activeField = nil
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
            .accessibilityLabel("Back to Login screen")
            
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
    
    @ViewBuilder
    private func inputCard(
        iconSystemName: String,
        placeholder: String,
        text: Binding<String>,
        error: String?,
        fieldType: Field,
        keyboardType: UIKeyboardType,
        autoCapitalize: TextInputAutocapitalization
    ) -> some View {
        VStack(alignment: .leading, spacing: AppSpacing.xSmall) {
            HStack {
                Image(systemName: iconSystemName)
                    .font(.system(size: 16))
                    .foregroundStyle(activeField == fieldType ? AppColors.primary : AppColors.textMuted)
                
                TextField(placeholder, text: text)
                    .font(AppTypography.body)
                    .foregroundStyle(AppColors.textPrimary)
                    .keyboardType(keyboardType)
                    .textInputAutocapitalization(autoCapitalize)
                    .autocorrectionDisabled()
                    .focused($activeField, equals: fieldType)
                    .onChange(of: text.wrappedValue) { _ in
                        clearFieldError(for: fieldType)
                    }
                
                if !text.wrappedValue.isEmpty {
                    Button {
                        text.wrappedValue = ""
                        clearFieldError(for: fieldType)
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 16))
                            .foregroundStyle(AppColors.textMuted)
                    }
                    .accessibilityLabel("Clear \(placeholder)")
                }
            }
            .padding(AppSpacing.medium)
            .background(AppColors.cardSurface)
            .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
            .overlay(
                RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                    .stroke(
                        error != nil ? AppColors.error : (activeField == fieldType ? AppColors.primary : Color.white.opacity(0.12)),
                        lineWidth: activeField == fieldType || error != nil ? 1.5 : 1
                    )
            )
            
            if let error {
                Text(error)
                    .font(AppTypography.caption)
                    .foregroundStyle(AppColors.error)
                    .padding(.leading, AppSpacing.xSmall)
            }
        }
    }
    
    private func clearFieldError(for field: Field) {
        switch field {
        case .name: nameError = nil
        case .email: emailError = nil
        case .mobile: mobileError = nil
        }
    }
    
    private func validateAndSubmit() {
        var isValid = true
        
        let trimmedName = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedName.count < 2 {
            nameError = "Please enter your full name."
            isValid = false
        } else {
            nameError = nil
        }
        
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let isEmailValid = trimmedEmail.contains("@") && trimmedEmail.contains(".") && trimmedEmail.count >= 5
        if !isEmailValid {
            emailError = "Please enter a valid email address."
            isValid = false
        } else {
            emailError = nil
        }
        
        let trimmedMobile = mobileNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let isMobileValid = trimmedMobile.allSatisfy({ $0.isNumber || $0 == "+" || $0 == "-" || $0 == " " }) && trimmedMobile.count >= 7
        if !isMobileValid {
            mobileError = "Please enter a valid mobile number."
            isValid = false
        } else {
            mobileError = nil
        }
        
        if isValid {
            let primaryContact = !trimmedEmail.isEmpty ? trimmedEmail : trimmedMobile
            onRegister(primaryContact, trimmedName)
        }
    }
}

#Preview {
    RegisterView(
        onRegister: { _, _ in },
        onBack: {}
    )
}
