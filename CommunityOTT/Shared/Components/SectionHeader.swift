//
//  SectionHeader.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct SectionHeader: View {
    let title: String
    let subtitle: String?
    let seeAllAction: (() -> Void)?
    
    public init(title: String, subtitle: String? = nil, seeAllAction: (() -> Void)? = nil) {
        self.title = title
        self.subtitle = subtitle
        self.seeAllAction = seeAllAction
    }
    
    public var body: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: AppSpacing.xxSmall) {
                Text(title)
                    .font(AppTypography.title2)
                    .foregroundStyle(AppColors.textPrimary)
                
                if let subtitle {
                    Text(subtitle)
                        .font(AppTypography.caption)
                        .foregroundStyle(AppColors.textSecondary)
                }
            }
            
            Spacer()
            
            if let seeAllAction {
                Button("See All", action: seeAllAction)
                    .font(AppTypography.subheadline)
                    .foregroundStyle(AppColors.primary)
            }
        }
        .padding(.horizontal, AppSpacing.medium)
    }
}
