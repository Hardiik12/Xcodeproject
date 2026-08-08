//
//  PodcastCardView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct PodcastCardView: View {
    let item: ContentItem
    let action: () -> Void
    
    public init(item: ContentItem, action: @escaping () -> Void) {
        self.item = item
        self.action = action
    }
    
    public var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: AppSpacing.xxSmall) {
                // 1:1 Square Podcast Artwork
                ZStack(alignment: .bottomLeading) {
                    RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium)
                        .fill(AppColors.cardSurface)
                        .aspectRatio(1/1, contentMode: .fit)
                        .overlay(
                            ZStack {
                                LinearGradient(
                                    colors: [AppColors.primary.opacity(0.3), AppColors.secondary],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                                
                                VStack(spacing: AppSpacing.xxSmall) {
                                    Image(systemName: "mic.fill")
                                        .font(.system(size: 32))
                                        .foregroundStyle(AppColors.primary)
                                    
                                    Image(systemName: "waveform")
                                        .font(.system(size: 16))
                                        .foregroundStyle(AppColors.textSecondary.opacity(0.7))
                                }
                            }
                        )
                        .clipShape(RoundedRectangle(cornerRadius: AppSpacing.CornerRadius.medium))
                    
                    Text("PODCAST")
                        .font(AppTypography.badge)
                        .padding(.horizontal, AppSpacing.xxSmall + 2)
                        .padding(.vertical, AppSpacing.xxSmall)
                        .background(Color.black.opacity(0.7))
                        .foregroundStyle(AppColors.primary)
                        .cornerRadius(AppSpacing.CornerRadius.small)
                        .padding(AppSpacing.xSmall)
                }
                
                Text(item.title)
                    .font(AppTypography.subheadline)
                    .foregroundStyle(AppColors.textPrimary)
                    .lineLimit(1)
                
                Text(item.durationFormatted)
                    .font(AppTypography.caption)
                    .foregroundStyle(AppColors.textSecondary)
                    .lineLimit(1)
            }
            .frame(width: 140)
        }
        .buttonStyle(.cardPress)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Podcast: \(item.title), duration \(item.durationFormatted)")
    }
}

#Preview {
    PodcastCardView(
        item: ContentItem(
            id: "vos-1",
            title: "Grassroots Founder",
            description: "Podcast episode",
            category: "Podcast",
            type: .podcast
        ),
        action: {}
    )
}
