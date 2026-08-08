//
//  APIClient.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation

public protocol APIClientProtocol: Sendable {
    func request<T: Decodable>(_ endpoint: APIEndpoint) async throws -> T
}

public final class URLSessionAPIClient: APIClientProtocol, @unchecked Sendable {
    private let baseURL: URL
    private let session: URLSession
    private let jsonDecoder: JSONDecoder
    
    public init(baseURL: URL, session: URLSession = .shared, jsonDecoder: JSONDecoder = JSONDecoder()) {
        self.baseURL = baseURL
        self.session = session
        self.jsonDecoder = jsonDecoder
    }
    
    public func request<T: Decodable>(_ endpoint: APIEndpoint) async throws -> T {
        var urlComponents = URLComponents(url: baseURL.appendingPathComponent(endpoint.path), resolvingAgainstBaseURL: true)
        
        if let queryParams = endpoint.queryParameters {
            urlComponents?.queryItems = queryParams.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        
        guard let url = urlComponents?.url else {
            throw APIError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = endpoint.method.rawValue
        request.httpBody = endpoint.body
        
        endpoint.headers?.forEach { key, value in
            request.addValue(value, forHTTPHeaderField: key)
        }
        
        if request.value(forHTTPHeaderField: "Content-Type") == nil {
            request.addValue("application.json", forHTTPHeaderField: "Content-Type")
        }
        
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw APIError.networkError(error.localizedDescription)
        }
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.unknown
        }
        
        guard (200...299).contains(httpResponse.statusCode) else {
            if httpResponse.statusCode == 401 {
                throw APIError.unauthorized
            }
            throw APIError.serverError(statusCode: httpResponse.statusCode)
        }
        
        do {
            return try jsonDecoder.decode(T.self, from: data)
        } catch let decodingError {
            throw APIError.decodingError(decodingError.localizedDescription)
        }
    }
}
