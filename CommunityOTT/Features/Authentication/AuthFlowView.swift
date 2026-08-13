//
//  AuthFlowView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public enum AuthFlowStep {
    case landing
    case login
    case otp(input: String, name: String?)
    case register
}

@MainActor
public struct AuthFlowView: View {
    let authService: AuthServiceProtocol
    let onAuthenticate: () -> Void
    let onContinueAsGuest: () -> Void
    
    @State private var currentStep: AuthFlowStep = .landing
    
    public init(
        authService: AuthServiceProtocol = MockAuthService(),
        onAuthenticate: @escaping () -> Void,
        onContinueAsGuest: @escaping () -> Void
    ) {
        self.authService = authService
        self.onAuthenticate = onAuthenticate
        self.onContinueAsGuest = onContinueAsGuest
    }
    
    public var body: some View {
        Group {
            switch currentStep {
            case .landing:
                LandingView(
                    onGetStarted: {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            currentStep = .login
                        }
                    },
                    onSignIn: {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            currentStep = .login
                        }
                    },
                    onContinueAsGuest: onContinueAsGuest
                )
                
            case .login:
                LoginView(
                    onContinue: { userInput in
                        withAnimation(.easeInOut(duration: 0.3)) {
                            currentStep = .otp(input: userInput, name: nil)
                        }
                    },
                    onCreateAccount: {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            currentStep = .register
                        }
                    },
                    onContinueAsGuest: onContinueAsGuest,
                    onBack: {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            currentStep = .landing
                        }
                    }
                )
                
            case .otp(let input, let name):
                OTPVerificationView(
                    input: input,
                    name: name,
                    onVerifySuccess: { verifiedInput, registeredName in
                        Task {
                            _ = try? await authService.login(name: registeredName, email: verifiedInput)
                            await MainActor.run {
                                onAuthenticate()
                            }
                        }
                    },
                    onChangeDestination: {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            currentStep = name != nil ? .register : .login
                        }
                    },
                    onBack: {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            currentStep = name != nil ? .register : .login
                        }
                    }
                )
                
            case .register:
                RegisterView(
                    onRegister: { userInput, registeredName in
                        withAnimation(.easeInOut(duration: 0.3)) {
                            currentStep = .otp(input: userInput, name: registeredName)
                        }
                    },
                    onBack: {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            currentStep = .login
                        }
                    }
                )
            }
        }
    }
}

#Preview {
    AuthFlowView(onAuthenticate: {}, onContinueAsGuest: {})
}
