//
//  AuthFlowView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct AuthFlowView: View {
    let onAuthenticate: () -> Void
    let onContinueAsGuest: () -> Void
    
    public init(onAuthenticate: @escaping () -> Void, onContinueAsGuest: @escaping () -> Void) {
        self.onAuthenticate = onAuthenticate
        self.onContinueAsGuest = onContinueAsGuest
    }
    
    public var body: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            
            VStack(spacing: AppSpacing.large) {
                Spacer()
                
                VStack(spacing: AppSpacing.medium) {
                    Image(systemName: "person.crop.square.fill")
                        .font(.system(size: 64))
                        .foregroundStyle(AppColors.primary)
                    
                    Text("Welcome to CommunityOTT")
                        .font(AppTypography.heroTitle)
                        .foregroundStyle(AppColors.textPrimary)
                        .multilineTextAlignment(.center)
                    
                    Text("Stream authentic cultural stories, documentaries, podcasts, and community entertainment.")
                        .font(AppTypography.body)
                        .foregroundStyle(AppColors.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, AppSpacing.large)
                }
                
                Spacer()
                
                VStack(spacing: AppSpacing.medium) {
                    PrimaryButton(
                        title: "Sign In / Register",
                        iconSystemName: "arrow.right.circle.fill",
                        action: onAuthenticate
                    )
                    
                    SecondaryButton(
                        title: "Explore as Guest",
                        iconSystemName: "eye.fill",
                        action: onContinueAsGuest
                    )
                }
                .padding(.horizontal, AppSpacing.large)
                .padding(.bottom, AppSpacing.xLarge)
            }
        }
    }
}

#Preview {
    AuthFlowView(onAuthenticate: {}, onContinueAsGuest: {})
}
