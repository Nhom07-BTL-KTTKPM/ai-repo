package iuh.fit.aiservice.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AiChatServiceTest {

    @Test
    void testExtractAndStripTaggedProductIds() {
        // Instantiate service with nulls since the utility methods do not use dependencies
        AiChatService service = new AiChatService(null, null, null, null, null, null, null, null, null, null);

        // Test Case 1: Standard [**uuid**] format
        String text1 = "Paula's Choice Clear SPF30+ có sẵn. [**a2000000-0000-0000-0000-000000000023**] Thử trước trên da.";
        List<String> ids1 = service.extractTaggedProductIds(text1);
        assertEquals(1, ids1.size());
        assertEquals("a2000000-0000-0000-0000-000000000023", ids1.get(0));

        String stripped1 = service.stripTaggedProductIds(text1);
        assertEquals("Paula's Choice Clear SPF30+ có sẵn.  Thử trước trên da.", stripped1);

        // Test Case 2: Bold **uuid** format without brackets (which was causing the issue)
        String text2 = "Paula's Choice Clear SPF30+ có sẵn. **a2000000-0000-0000-0000-000000000023**";
        List<String> ids2 = service.extractTaggedProductIds(text2);
        assertEquals(1, ids2.size());
        assertEquals("a2000000-0000-0000-0000-000000000023", ids2.get(0));

        String stripped2 = service.stripTaggedProductIds(text2);
        assertEquals("Paula's Choice Clear SPF30+ có sẵn.", stripped2);

        // Test Case 3: Bare uuid format
        String text3 = "Paula's Choice Clear SPF30+ có sẵn. a2000000-0000-0000-0000-000000000023";
        List<String> ids3 = service.extractTaggedProductIds(text3);
        assertEquals(1, ids3.size());
        assertEquals("a2000000-0000-0000-0000-000000000023", ids3.get(0));

        String stripped3 = service.stripTaggedProductIds(text3);
        assertEquals("Paula's Choice Clear SPF30+ có sẵn.", stripped3);

        // Test Case 4: Multiple UUIDs mixed formats
        String text4 = "Products: [**a2000000-0000-0000-0000-000000000023**] and **a2000000-0000-0000-0000-000000000024** are good.";
        List<String> ids4 = service.extractTaggedProductIds(text4);
        assertEquals(2, ids4.size());
        assertTrue(ids4.contains("a2000000-0000-0000-0000-000000000023"));
        assertTrue(ids4.contains("a2000000-0000-0000-0000-000000000024"));

        String stripped4 = service.stripTaggedProductIds(text4);
        assertEquals("Products:  and  are good.", stripped4);
    }
}
