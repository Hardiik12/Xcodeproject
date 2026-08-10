//
//  AppRootView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct AppRootView: View {
    @StateObject private var viewModel = RootViewModel()
    @StateObject private var onboardingStore = OnboardingStateStore.shared
    
    public init() {}
    
    public var body: some View {
        Group {
            if !onboardingStore.hasCompletedOnboarding {
                OnboardingView {
                    withAnimation(.easeInOut(duration: 0.4)) {
                        onboardingStore.completeOnboarding()
                    }
                }
            } else {
                switch viewModel.state {
                case .splash:
                    SplashView()
                        .task {
                            await viewModel.bootstrap()
                        }
                case .unauthenticated:
                    AuthFlowView(
                        onAuthenticate: {
                            viewModel.authenticateUser()
                        },
                        onContinueAsGuest: {
                            viewModel.continueAsGuest()
                        }
                    )
                case .authenticated:
                    AppShellView(
                        onSignOut: {
                            viewModel.signOut()
                        }
                    )
                }
            }
        }
    }
}

#Preview {
    AppRootView()
}
