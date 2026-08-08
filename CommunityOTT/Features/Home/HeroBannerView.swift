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
            // Hero Image Background with Cinematic Gradient Overlay
            RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.large)
                .fill(AppColors.cardSurface)
                .aspectRatio(16/9, contentMode: .fit)
                .overlay(
                    ZStack {
                        VStack(spacing: AppSpacing.xxSmall) {
                            Image(systemName: "film.fill")
                                .font(.system(size: 56))
                                .foregroundStyle(AppColors.primary.opacity(0.18))
                            Text("HERO IMAGE")
                                .font(AppTypography.badge)
                                .foregroundStyle(AppColors.textMuted.opacity(0.4))
                        }
                        
                        AppColors.heroGradient
                    }
                )
                .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.large))
            
            // Hero Content & Inline Actions
            VStack(spacing: AppSpacing.xSmall) {
                Text(item.title)
                    .font(AppTypography.heroTitle)
                    .foregroundStyle(AppColors.textPrimary)
                    .multilineTextAlignment(.center)
                
                Text(item.subtitleMetadata)
                    .font(AppTypography.subheadline)
                    .foregroundStyle(AppColors.textSecondary)
                    .multilineTextAlignment(.center)
                
                HStack(spacing: AppSpacing.medium) {
                    PrimaryButton(
                        title: "Watch",
                        iconSystemName: "play.fill",
                        action: onWatch
                    )
                    
                    SecondaryButton(
                        title: "List",
                        iconSystemName: "plus",
                        action: onMyList
                    )
                }
                .padding(.horizontal, AppSpacing.medium)
                .padding(.top, AppSpacing.xxSmall)
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
            title: "Stories of Heritage",
            description: "Discover stories that deserve to be remembered.",
            category: "Documentary",
            type: .documentary,
            durationInSeconds: 2520
        ),
        onWatch: {},
        onMyList: {}
    )
}
