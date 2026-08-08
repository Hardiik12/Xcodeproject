//
//  StateViews.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct LoadingView: View {
    let message: String
    
    public init(message: String = "Loading CommunityOTT...") {
        self.message = message
    }
    
    public var body: some View {
        VStack(spacing: AppSpacing.medium) {
            ProgressView()
                .tint(AppColors.primary)
                .scaleEffect(1.2)
            Text(message)
                .font(AppTypography.body)
                .foregroundStyle(AppColors.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AppColors.background)
    }
}

public struct EmptyStateView: View {
    let title: String
    let description: String
    let iconSystemName: String
    
    public init(
        title: String = "No Content Available",
        description: String = "Check back soon for new community stories and cultural releases.",
        iconSystemName: String = "film"
    ) {
        self.title = title
        self.description = description
        self.iconSystemName = iconSystemName
    }
    
    public var body: some View {
        VStack(spacing: AppSpacing.medium) {
            Image(systemName: iconSystemName)
                .font(.system(size: 48))
                .foregroundStyle(AppColors.primary.opacity(0.8))
            
            Text(title)
                .font(AppTypography.title2)
                .foregroundStyle(AppColors.textPrimary)
            
            Text(description)
                .font(AppTypography.body)
                .foregroundStyle(AppColors.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, AppSpacing.large)
        }
        .padding(AppSpacing.large)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AppColors.background)
    }
}

public struct ErrorStateView: View {
    let message: String
    let retryAction: () -> Void
    
    public init(message: String, retryAction: @escaping () -> Void) {
        self.message = message
        self.retryAction = retryAction
    }
    
    public var body: some View {
        VStack(spacing: AppSpacing.medium) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 48))
                .foregroundStyle(AppColors.error)
            
            Text("Something Went Wrong")
                .font(AppTypography.title2)
                .foregroundStyle(AppColors.textPrimary)
            
            Text(message)
                .font(AppTypography.body)
                .foregroundStyle(AppColors.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, AppSpacing.large)
            
            PrimaryButton(title: "Retry", iconSystemName: "arrow.clockwise", action: retryAction)
                .frame(maxWidth: 200)
                .padding(.top, AppSpacing.small)
        }
        .padding(AppSpacing.large)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AppColors.background)
    }
}
