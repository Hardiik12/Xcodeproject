//
//  OTPVerificationView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI
import Combine

public struct OTPVerificationView: View {
    let input: String
    let name: String?
    let onVerifySuccess: (String, String?) -> Void
    let onChangeDestination: () -> Void
    let onBack: () -> Void
    
    @State private var digits: [String] = Array(repeating: "", count: 6)
    @FocusState private var activeFieldIndex: Int?
    
    @State private var errorMessage: String? = nil
    @State private var cooldownSeconds: Int = 30
    @State private var isTimerActive: Bool = true
    
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    
    public init(
        input: String,
        name: String? = nil,
        onVerifySuccess: @escaping (String, String?) -> Void,
        onChangeDestination: @escaping () -> Void,
        onBack: @escaping () -> Void
    ) {
        self.input = input
        self.name = name
        self.onVerifySuccess = onVerifySuccess
        self.onChangeDestination = onChangeDestination
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
                        Spacer().frame(height: 12)
                        
                        // Title & Subtitle Branding Header
                        VStack(spacing: AppSpacing.xSmall) {
                            Text("Verify your account")
                                .font(AppTypography.heroTitle)
                                .foregroundStyle(AppColors.textPrimary)
                            
                            Text("Enter the verification code sent to")
                                .font(AppTypography.body)
                                .foregroundStyle(AppColors.textSecondary)
                            
                            Text(maskedDestination)
                                .font(AppTypography.headline)
                                .foregroundStyle(AppColors.primary)
                                .padding(.top, 2)
                        }
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, AppSpacing.medium)
                        
                        // 6-Digit OTP Field Container
                        VStack(spacing: AppSpacing.medium) {
                            HStack(spacing: 8) {
                                ForEach(0..<6, id: \.self) { index in
                                    digitBox(at: index)
                                }
                            }
                            .padding(.horizontal, AppSpacing.medium)
                            
                            if let errorMessage {
                                Text(errorMessage)
                                    .font(AppTypography.caption)
                                    .foregroundStyle(AppColors.error)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, AppSpacing.medium)
                            }
                        }
                        
                        // Primary Action Button (Verify)
                        PrimaryButton(
                            title: "Verify",
                            iconSystemName: "checkmark.circle.fill"
                        ) {
                            submitOTP()
                        }
                        .disabled(otpCode.count < 6)
                        .opacity(otpCode.count < 6 ? 0.5 : 1.0)
                        .padding(.horizontal, AppSpacing.large)
                        .accessibilityLabel("Verify OTP code button")
                        
                        // Resend Code Section with Cooldown Timer
                        VStack(spacing: AppSpacing.xSmall) {
                            Text("Didn't receive the code?")
                                .font(AppTypography.caption)
                                .foregroundStyle(AppColors.textMuted)
                            
                            if isTimerActive && cooldownSeconds > 0 {
                                Text("Resend code in \(cooldownSeconds)s")
                                    .font(AppTypography.subheadline)
                                    .foregroundStyle(AppColors.textSecondary)
                            } else {
                                Button {
                                    restartCooldownTimer()
                                } label: {
                                    Text("Resend code")
                                        .font(AppTypography.headline)
                                        .foregroundStyle(AppColors.primary)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Resend OTP verification code button")
                            }
                        }
                        .padding(.vertical, AppSpacing.xSmall)
                        
                        // Change Destination Footer Link
                        Button {
                            onChangeDestination()
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "pencil")
                                    .font(.system(size: 13))
                                Text("Change email or mobile number")
                                    .font(AppTypography.subheadline)
                            }
                            .foregroundStyle(AppColors.textSecondary)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Change email or mobile number link")
                        .padding(.bottom, AppSpacing.large)
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            activeFieldIndex = 0
        }
        .onReceive(timer) { _ in
            if isTimerActive && cooldownSeconds > 0 {
                cooldownSeconds -= 1
                if cooldownSeconds == 0 {
                    isTimerActive = false
                }
            }
        }
        .onTapGesture {
            activeFieldIndex = nil
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
            .accessibilityLabel("Back to previous screen")
            
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
    
    private var otpCode: String {
        digits.joined()
    }
    
    private var maskedDestination: String {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.contains("@"), let atIndex = trimmed.firstIndex(of: "@") {
            let prefix = String(trimmed[..<atIndex])
            let domain = String(trimmed[atIndex...])
            if prefix.count <= 2 {
                return "\(prefix)****\(domain)"
            } else {
                let visible = String(prefix.prefix(2))
                return "\(visible)****\(domain)"
            }
        } else if trimmed.count >= 7 {
            let suffix = String(trimmed.suffix(4))
            return "+91 XXXXX \(suffix)"
        }
        return trimmed
    }
    
    @ViewBuilder
    private func digitBox(at index: Int) -> some View {
        TextField("", text: Binding(
            get: { digits[index] },
            set: { newValue in
                handleDigitInput(newValue: newValue, at: index)
            }
        ))
        .keyboardType(.numberPad)
        .multilineTextAlignment(.center)
        .font(.system(size: 24, weight: .bold, design: .monospaced))
        .foregroundStyle(AppColors.textPrimary)
        .frame(width: 48, height: 56)
        .background(AppColors.cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
        .overlay(
            RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                .stroke(
                    errorMessage != nil ? AppColors.error : (activeFieldIndex == index ? AppColors.primary : Color.white.opacity(0.12)),
                    lineWidth: activeFieldIndex == index || errorMessage != nil ? 1.5 : 1
                )
        )
        .focused($activeFieldIndex, equals: index)
        .accessibilityLabel("OTP digit box \(index + 1) of 6")
    }
    
    private func handleDigitInput(newValue: String, at index: Int) {
        if errorMessage != nil {
            errorMessage = nil
        }
        
        // Handle Paste of 6-digit code
        if newValue.count >= 6 {
            let cleanDigits = newValue.filter { $0.isNumber }
            if cleanDigits.count >= 6 {
                for i in 0..<6 {
                    let charIndex = cleanDigits.index(cleanDigits.startIndex, offsetBy: i)
                    digits[i] = String(cleanDigits[charIndex])
                }
                activeFieldIndex = 5
                return
            }
        }
        
        let filtered = newValue.filter { $0.isNumber }
        if filtered.isEmpty {
            digits[index] = ""
            if index > 0 {
                activeFieldIndex = index - 1
            }
        } else {
            let lastChar = String(filtered.last!)
            digits[index] = lastChar
            if index < 5 {
                activeFieldIndex = index + 1
            }
        }
    }
    
    private func submitOTP() {
        let code = otpCode
        if code == "123456" {
            errorMessage = nil
            onVerifySuccess(input, name)
        } else {
            errorMessage = "Incorrect verification code. Please check the code and try again."
            digits = Array(repeating: "", count: 6)
            activeFieldIndex = 0
        }
    }
    
    private func restartCooldownTimer() {
        cooldownSeconds = 30
        isTimerActive = true
        errorMessage = nil
    }
}

#Preview {
    OTPVerificationView(
        input: "hardik@communityott.org",
        name: "Hardik Gupta",
        onVerifySuccess: { _, _ in },
        onChangeDestination: {},
        onBack: {}
    )
}
