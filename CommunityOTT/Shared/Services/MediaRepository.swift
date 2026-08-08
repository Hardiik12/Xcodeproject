//
//  MediaRepository.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation

public protocol MediaRepositoryProtocol: Sendable {
    func fetchMediaStream(for contentID: String) async throws -> MediaStream
}
