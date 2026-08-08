//
//  AppRootView.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public struct AppRootView: View {
    @StateObject private var viewModel = RootViewModel()
    
    public init() {}
    
    public var body: some View {
        Group {
            switch viewModel.state {
            case .splash:
                SplashView()
                    .task {
                        await viewModel.bootstrap()
                    }
            case .unauthenticated:
                AuthFlowView(
                    onAuthenticate: {
                        viewModel.authenticateUser()
                    },
                    onContinueAsGuest: {
                        viewModel.continueAsGuest()
                    }
                )
            case .authenticated:
                AppShellView(
                    onSignOut: {
                        viewModel.signOut()
                    }
                )
            }
        }
    }
}

#Preview {
    AppRootView()
}
