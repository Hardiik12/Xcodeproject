//
//  OnboardingPageView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI

public struct OnboardingPageView: View {
    public init() {}
    
    public var body: some View {
        VStack(spacing: AppSpacing.large) {
            ZStack(alignment: .bottom) {
                Image("hero_heritage")
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(height: 280)
                    .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.large))
                    .overlay(
                        RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.large)
                            .stroke(AppColors.primary.opacity(0.3), lineWidth: 1)
                    )
                
                LinearGradient(
                    colors: [Color.clear, AppColors.background.opacity(0.9)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.large))
            }
            .padding(.horizontal, AppSpacing.medium)
            
            VStack(spacing: AppSpacing.small) {
                Text("CommunityOTT")
                    .font(AppTypography.title1)
                    .foregroundStyle(AppColors.textPrimary)
                
                Text("\"Our Story. Our Stage. Our Future.\"")
                    .font(AppTypography.headline)
                    .foregroundStyle(AppColors.primary)
                    .italic()
                
                Text("A home for culture, stories, achievements and community voices.")
                    .font(AppTypography.body)
                    .foregroundStyle(AppColors.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, AppSpacing.large)
            }
        }
    }
}
