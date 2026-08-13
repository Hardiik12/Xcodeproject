//
//  SplashView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

@MainActor
public struct SplashView: View {
    @State private var logoScale: CGFloat = 0.8
    @State private var logoOpacity: Double = 0.0
    
    public init() {}
    
    public var body: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            
            VStack(spacing: AppSpacing.large) {
                Spacer()
                
                VStack(spacing: AppSpacing.small) {
                    ZStack {
                        Circle()
                            .fill(AppColors.secondary.opacity(0.3))
                            .frame(width: 120, height: 120)
                        
                        Image(systemName: "play.tv.fill")
                            .font(.system(size: 56, weight: .bold))
                            .foregroundStyle(AppColors.primary)
                    }
                    .scaleEffect(logoScale)
                    .opacity(logoOpacity)
                    
                    Text("CommunityOTT")
                        .font(AppTypography.heroTitle)
                        .foregroundStyle(AppColors.textPrimary)
                        .opacity(logoOpacity)
                    
                    Text("Empowering Culture & Community Stories")
                        .font(AppTypography.subheadline)
                        .foregroundStyle(AppColors.textSecondary)
                        .opacity(logoOpacity)
                }
                
                Spacer()
                
                ProgressView()
                    .tint(AppColors.primary)
                    .padding(.bottom, AppSpacing.xxLarge)
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.8)) {
                logoScale = 1.0
                logoOpacity = 1.0
            }
        }
    }
}

#Preview {
    SplashView()
}
