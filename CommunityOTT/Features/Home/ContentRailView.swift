//
//  ContentRailView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public enum RailVariant {
    case continueWatching
    case poster
    case podcast
    case landscape
}

public struct ContentRailView: View {
    let title: String
    let subtitle: String?
    let items: [ContentItem]
    let variant: RailVariant
    let onSelect: (ContentItem) -> Void
    let onSeeAll: (() -> Void)?
    
    public init(
        title: String,
        subtitle: String? = nil,
        items: [ContentItem],
        variant: RailVariant = .landscape,
        onSelect: @escaping (ContentItem) -> Void,
        onSeeAll: (() -> Void)? = nil
    ) {
        self.title = title
        self.subtitle = subtitle
        self.items = items
        self.variant = variant
        self.onSelect = onSelect
        self.onSeeAll = onSeeAll
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.small) {
            SectionHeader(title: title, subtitle: subtitle, seeAllAction: onSeeAll)
            
            if items.isEmpty {
                Text("No items available in this section.")
                    .font(AppTypography.caption)
                    .foregroundStyle(AppColors.textMuted)
                    .padding(.horizontal, AppSpacing.medium)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: AppSpacing.medium) {
                        ForEach(items) { item in
                            cardView(for: item)
                        }
                    }
                    .padding(.horizontal, AppSpacing.medium)
                }
            }
        }
    }
    
    @ViewBuilder
    private func cardView(for item: ContentItem) -> some View {
        switch variant {
        case .continueWatching:
            ContinueWatchingCardView(item: item) {
                onSelect(item)
            }
        case .poster:
            PosterCardView(item: item) {
                onSelect(item)
            }
        case .podcast:
            PodcastCardView(item: item) {
                onSelect(item)
            }
        case .landscape:
            LandscapeCardView(item: item) {
                onSelect(item)
            }
        }
    }
}
