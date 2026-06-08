import XCTest
@testable import otto_mobile

final class ChatOutgoingImageURLNormalizerTests: XCTestCase {
    func testPromotesDirectGifURLAndStripsBody() {
        let url = "https://static.klipy.com/example/test.gif"
        let result = ChatOutgoingImageURLNormalizer.normalize(
            draft: url,
            pendingAttachment: nil
        )
        XCTAssertEqual(result.imageUrl, url)
        XCTAssertEqual(result.body, "")
        XCTAssertNil(result.klipyShare)
    }

    func testCaptionRemainsWhenGifURLAndText() {
        let url = "https://cdn.example.com/a.gif"
        let result = ChatOutgoingImageURLNormalizer.normalize(
            draft: "\(url) nice one",
            pendingAttachment: nil
        )
        XCTAssertEqual(result.imageUrl, url)
        XCTAssertEqual(result.body, "nice one")
    }

    func testNonImageURLStaysInBody() {
        let result = ChatOutgoingImageURLNormalizer.normalize(
            draft: "https://example.com/article",
            pendingAttachment: nil
        )
        XCTAssertNil(result.imageUrl)
        XCTAssertEqual(result.body, "https://example.com/article")
    }

    func testKlipyAttachmentWinsOverDraftURL() {
        let selection = KlipyGifSelection(
            slug: "test-1",
            title: "Hi",
            previewURL: URL(string: "https://static.klipy.com/p.gif")!,
            sendURL: URL(string: "https://static.klipy.com/s.gif")!,
            width: 100,
            height: 100
        )
        let pending = ChatPendingComposerAttachment(
            kind: .klipyGif(selection),
            klipySearchQuery: "hello"
        )
        let result = ChatOutgoingImageURLNormalizer.normalize(
            draft: "caption",
            pendingAttachment: pending
        )
        XCTAssertEqual(result.imageUrl, selection.sendURL.absoluteString)
        XCTAssertEqual(result.body, "caption")
        XCTAssertEqual(result.klipyShare?.slug, "test-1")
        XCTAssertEqual(result.klipyShare?.searchQuery, "hello")
    }
}
