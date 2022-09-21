#nullable enable

using Microsoft.AspNetCore.Builder;
using Microsoft.Extensions.DependencyInjection;

namespace UbisoftConnectProxy
{
    public class Startup
    {
        public void Configure(IApplicationBuilder app)
        {
            var synchronizer = app.ApplicationServices.GetRequiredService<Synchronizer>();
            IProxy proxy = new JavaProxy(synchronizer);
            app.Use(async (context, next) =>
            {
                await proxy.ForwardAsync(context);
                await next();
            });
        }
    }
}
