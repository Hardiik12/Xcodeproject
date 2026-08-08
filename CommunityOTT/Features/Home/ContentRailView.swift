//
//  ContentRailView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct ContentRailView: View {
    let title: String
    let subtitle: String?
    let items: [ContentItem]
    let onSelect: (ContentItem) -> Void
    let onSeeAll: (() -> Void)?
    
    public init(
        title: String,
        subtitle: String? = nil,
        items: [ContentItem],
        onSelect: @escaping (ContentItem) -> Void,
        onSeeAll: (() -> Void)? = nil
    ) {
        self.title = title
        self.subtitle = subtitle
        self.items = items
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
                            ContentCard(item: item) {
                                onSelect(item)
                            }
                        }
                    }
                    .padding(.horizontal, AppSpacing.medium)
                }
            }
        }
    }
}
