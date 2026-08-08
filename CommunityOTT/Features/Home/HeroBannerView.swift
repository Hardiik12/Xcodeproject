//
//  HeroBannerView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct HeroBannerView: View {
    let item: ContentItem
    let onWatch: () -> Void
    let onMyList: () -> Void
    
    public init(item: ContentItem, onWatch: @escaping () -> Void, onMyList: @escaping () -> Void) {
        self.item = item
        self.onWatch = onWatch
        self.onMyList = onMyList
    }
    
    public var body: some View {
        ZStack(alignment: .bottom) {
            // Background Surface with Gradient Overlay
            RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.large)
                .fill(AppColors.cardSurface)
                .aspectRatio(16/9, contentMode: .fit)
                .overlay(
                    ZStack {
                        Image(systemName: "film.fill")
                            .font(.system(size: 64))
                            .foregroundStyle(AppColors.primary.opacity(0.15))
                        
                        AppColors.heroGradient
                    }
                )
                .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.large))
            
            // Hero Content & Controls
            VStack(spacing: AppSpacing.small) {
                Text(item.category.uppercased())
                    .font(AppTypography.badge)
                    .foregroundStyle(AppColors.primary)
                    .padding(.horizontal, AppSpacing.small)
                    .padding(.vertical, AppSpacing.xxSmall)
                    .background(AppColors.secondary.opacity(0.8))
                    .cornerRadius(AppSpacing.CornerRadius.small)
                
                Text(item.title)
                    .font(AppTypography.heroTitle)
                    .foregroundStyle(AppColors.textPrimary)
                    .multilineTextAlignment(.center)
                
                Text(item.description)
                    .font(AppTypography.body)
                    .foregroundStyle(AppColors.textSecondary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .padding(.horizontal, AppSpacing.medium)
                
                HStack(spacing: AppSpacing.medium) {
                    PrimaryButton(
                        title: "Watch",
                        iconSystemName: "play.fill",
                        action: onWatch
                    )
                    
                    SecondaryButton(
                        title: "My List",
                        iconSystemName: "plus",
                        action: onMyList
                    )
                }
                .padding(.horizontal, AppSpacing.medium)
                .padding(.top, AppSpacing.xSmall)
            }
            .padding(.vertical, AppSpacing.medium)
        }
        .padding(.horizontal, AppSpacing.medium)
    }
}

#Preview {
    HeroBannerView(
        item: ContentItem(
            id: "hero",
            title: "Stories of Our Heritage",
            description: "Discover stories that deserve to be remembered.",
            category: "Featured Documentary",
            type: .documentary
        ),
        onWatch: {},
        onMyList: {}
    )
}
